package io.github.projectunified.craftcommand.processor.generator;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.FieldSpec;
import com.palantir.javapoet.JavaFile;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import io.github.projectunified.craftcommand.processor.TypeNames;
import com.palantir.javapoet.TypeSpec;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.extension.MethodAnnotationHandler;
import io.github.projectunified.craftcommand.processor.extension.ParameterAnnotationHandler;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.StringJoiner;
import java.util.function.Predicate;

/**
 * Base generator shared by every platform.
 *
 * <p>A platform only supplies its entry points and platform types; routing, parameter resolution and suggestions
 * live here. The generator reads {@link CommandModel}s only and never parses annotations, so a new platform does
 * not need to know how the parser works.
 *
 * <p>To add a platform, extend this class, implement {@link #generateEntryMethods}, {@link #getWrapperClassSuffix},
 * {@link #getSenderTypeName}, and {@link #getManagerType}, then return it from
 * {@link CommandGeneratorFactory#create}.
 */
public abstract class BaseCommandGenerator implements CommandGenerator {
    protected static final String PERMISSION_KEY = "permission";
    protected static final String DEFAULT_PERMISSION_MESSAGE = "You do not have permission to execute this command.";

    private final ProcessingEnvironment processingEnvironment;
    private final TypeSupport typeSupport = TypeSupport.builtins();
    private final List<ParameterAnnotationHandler<?>> parameterHandlers;
    private final List<MethodAnnotationHandler<?>> methodHandlers;
    private final SenderTypeRegistry senderTypeRegistry = new SenderTypeRegistry();
    private final SuggestionGenerator suggestions = new SuggestionGenerator(this);
    private final ExecutionGenerator execution = new ExecutionGenerator(this);

    protected BaseCommandGenerator(ProcessingEnvironment processingEnvironment) {
        this.processingEnvironment = processingEnvironment;
        ClassLoader classLoader = getClass().getClassLoader();
        this.parameterHandlers = SpiLoader.loadParameterHandlers(classLoader);
        this.methodHandlers = SpiLoader.loadMethodHandlers(classLoader);
    }

    @Override
    public void generate(CommandModel model) throws IOException {
        buildWrapperClass(model);
    }

    // ── Platform hooks ──

    /**
     * The suffix appended to the command class name, for example {@code "$BukkitCommand"}.
     */
    protected abstract String getWrapperClassSuffix();

    /**
     * The platform's native sender type.
     */
    public abstract ClassName getSenderTypeName();

    /**
     * The {@code CommandManager} type, including its sender type argument.
     */
    protected abstract TypeName getManagerType();

    /**
     * Configures the generated class declaration (superclass, interfaces).
     */
    protected void configureClass(TypeSpec.Builder typeSpec, CommandModel model) {
    }

    /**
     * Adds platform-specific constructor statements before the instance and manager are assigned.
     */
    protected void addConstructorStatements(MethodSpec.Builder constructorBuilder, CommandModel model) {
    }

    /**
     * Adds the platform entry points ({@code execute}, {@code tabComplete}, {@code getCommandNode}, ...).
     */
    protected abstract void generateEntryMethods(TypeSpec.Builder typeSpec, CommandModel model);

    /**
     * Adds platform helper methods referenced from parameter types or entry points.
     */
    protected void generatePlatformHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
    }

    /**
     * Runs before a command method is executed, for example a permission check.
     */
    protected void onBeforeExecute(MethodSpec.Builder methodSpec, javax.lang.model.element.Element element, String returnStatement) {
    }

    /**
     * Emits the message shown when a command without arguments has no default action.
     */
    protected void generateUnknownSubcommandMessage(MethodSpec.Builder methodSpec, CommandModel model) {
        methodSpec.addStatement("manager.sendMessage(sender, $S, $S, $S)",
                "unknown-subcommand", "Unknown subcommand. Available: %s", getSubcommandNames(model));
    }

    /**
     * Wraps the raw sender variable in the platform expression that yields the sender object.
     */
    protected CodeBlock getSenderExpression(String senderVar) {
        return CodeBlock.of("$L", senderVar);
    }

    // ── Orchestration ──

    private void buildWrapperClass(CommandModel model) throws IOException {
        suggestions.beginWrapper();
        String wrapperClassName = model.getClassName().simpleName() + getWrapperClassSuffix();

        TypeSpec.Builder typeSpec = TypeSpec.classBuilder(wrapperClassName)
                .addOriginatingElement(model.getElement())
                .addJavadoc("Generated wrapper for {@link $T}.\n", model.getClassName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        configureClass(typeSpec, model);
        typeSpec.addField(FieldSpec.builder(model.getClassName(), "instance", Modifier.PRIVATE, Modifier.FINAL).build());
        typeSpec.addField(FieldSpec.builder(getManagerType(), "manager", Modifier.PRIVATE, Modifier.FINAL).build());

        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addModifiers(Modifier.PUBLIC)
                .addParameter(model.getClassName(), "instance")
                .addParameter(getManagerType(), "manager");
        addConstructorStatements(constructorBuilder, model);
        constructorBuilder.addStatement("this.instance = instance")
                .addStatement("this.manager = manager");
        generateSubcommandFieldsAndConstructors(model, typeSpec, constructorBuilder, "instance");
        typeSpec.addMethod(constructorBuilder.build());

        generateEntryMethods(typeSpec, model);
        generateHelpers(typeSpec, model);
        buildCommandInfo(typeSpec, model);

        JavaFile.builder(model.getPackageName(), typeSpec.build())
                .skipJavaLangImports(true)
                .build()
                .writeTo(processingEnvironment.getFiler());
    }

    /**
     * Generates the execution and suggestion routing plus the sender helpers of the whole command tree.
     */
    protected void generateHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        generateSubcommandClassExecutors(typeSpec, model, model);
        suggestions.buildParameterSuggestions(typeSpec, model, model);
        buildSenderCastHelpers(typeSpec, model);
        generatePlatformHelpers(typeSpec, model);
    }

    protected void generateSubcommandFieldsAndConstructors(CommandModel model, TypeSpec.Builder typeSpec, MethodSpec.Builder constructor, String parentFieldName) {
        for (CommandModel child : model.getNestedSubcommands()) {
            String fieldName = getSubcommandFieldName(child);
            typeSpec.addField(child.getClassName(), fieldName, Modifier.PRIVATE, Modifier.FINAL);
            if (child.getElement().getModifiers().contains(Modifier.STATIC)) {
                constructor.addStatement("this.$L = new $T()", fieldName, child.getClassName());
            } else {
                constructor.addStatement("this.$L = $L.new $L()", fieldName, parentFieldName, child.getClassName().simpleName());
            }
            generateSubcommandFieldsAndConstructors(child, typeSpec, constructor, fieldName);
        }
    }

    protected String getSubcommandFieldName(CommandModel child) {
        return Naming.subcommandField(child.getClassName());
    }

    public String getInstanceVarExpression(CommandModel classModel, CommandModel rootModel) {
        if (classModel == rootModel) {
            return "instance";
        }
        return "this." + getSubcommandFieldName(classModel);
    }

    public String getResolverInstanceExpr(MethodModel resolver, CommandModel rootModel) {
        TypeElement resolverClass = (TypeElement) resolver.element().getEnclosingElement();
        CommandModel resolverModel = rootModel.findModel(resolverClass);
        if (resolverModel != null) {
            return getInstanceVarExpression(resolverModel, rootModel);
        }
        return null;
    }

    /**
     * Emits the resolution of the command sender: a named {@code @Resolve} method, a registered sender resolver,
     * or a platform cast.
     */
    public void generateSenderResolution(MethodSpec.Builder spec, CommandModel rootModel, ParameterModel senderParam,
                                         TypeName senderType, String senderVar) {
        String resolveName = senderParam.resolveName();
        MethodModel resolver = resolveName != null && !resolveName.isEmpty() ? senderParam.resolverMethod() : null;
        String resolverInstance = resolver != null ? getResolverInstanceExpr(resolver, rootModel) : null;

        if (resolverInstance != null) {
            List<CodeBlock> args = new ArrayList<>();
            for (ParameterModel p : resolver.parameters()) {
                args.add(TypeNames.isStringArray(p.type()) ? CodeBlock.of("new String[0]") : CodeBlock.of("sender"));
            }
            CodeBlock invocation = call(resolverInstance, resolver.element().getSimpleName().toString(),
                    args.toArray(new CodeBlock[0]));
            TypeName returnType = TypeName.get(resolver.element().getReturnType());
            if (returnType.equals(senderType)) {
                spec.addStatement("$T $L = $L", senderType, senderVar, invocation);
            } else {
                spec.addStatement("$T $L = ($T) $L", senderType, senderVar, senderType, invocation);
            }
        } else if (resolveName != null) {
            generateManagerSenderResolution(spec, senderType, senderVar);
        } else if (!senderVar.equals("sender")) {
            if (isSenderBaseType(senderType)) {
                spec.addStatement("$T $L = sender", getSenderTypeName(), senderVar);
            } else if (isSenderType(senderType)) {
                spec.addStatement("$T $L = $L(sender)", senderType, senderVar, "as" + Naming.simpleName(senderType));
            } else {
                generateManagerSenderResolution(spec, senderType, senderVar);
            }
        }
    }

    private static void generateManagerSenderResolution(MethodSpec.Builder spec, TypeName senderType, String senderVar) {
        spec.addStatement("$T $L = manager.resolveSender($T.class, sender)",
                senderType, senderVar, senderType);
    }

    public void generateExecuteMethodBody(MethodSpec.Builder executeSpec, CommandModel model, String returnStatement) {
        executeSpec.beginControlFlow("try");
        execution.buildExecutionRouting(executeSpec, model, "args", "instance", model, returnStatement);
        executeSpec.nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("manager.handleException(sender, e)")
                .endControlFlow();
        executeSpec.addStatement("$L", returnStatement);
    }

    protected void generateSubcommandClassExecutors(TypeSpec.Builder typeSpec, CommandModel model, CommandModel rootModel) {
        for (CommandModel child : model.getNestedSubcommands()) {
            String childInstanceVar = "this." + getSubcommandFieldName(child);

            MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(Naming.executeHelper(child.getClassName()))
                    .addModifiers(Modifier.PRIVATE)
                    .addException(Exception.class)
                    .addParameter(getSenderTypeName(), "sender")
                    .addParameter(String[].class, "args");
            execution.buildExecutionRouting(methodSpec, child, "args", childInstanceVar, rootModel, "return");
            typeSpec.addMethod(methodSpec.build());

            if (suggestions.hasSuggestions(child)) {
                MethodSpec.Builder suggestMethodSpec = MethodSpec.methodBuilder(Naming.suggestHelper(child.getClassName()))
                        .addModifiers(Modifier.PRIVATE)
                        .returns(ParameterizedTypeName.get(List.class, String.class))
                        .addParameter(getSenderTypeName(), "sender")
                        .addParameter(String[].class, "args");
                buildSuggestionRouting(suggestMethodSpec, child, "args", rootModel);
                typeSpec.addMethod(suggestMethodSpec.build());
            }

            generateSubcommandClassExecutors(typeSpec, child, rootModel);
        }
    }

    /**
     * Generates tab completion routing for a command class, delegating to the suggestion generator.
     */
    protected void buildSuggestionRouting(MethodSpec.Builder methodSpec, CommandModel model, String argsVar, CommandModel rootModel) {
        suggestions.buildSuggestionRouting(methodSpec, model, argsVar, rootModel);
    }

    /**
     * Generates the sender cast helpers needed by a command class.
     */
    protected void buildSenderCastHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        suggestions.buildSenderCastHelpers(typeSpec, model);
    }

    /**
     * Generates the suggestion helpers of type-driven suggestions (boolean / platform built-ins). Platforms that
     * inline custom providers call this from their helper step.
     */
    protected void buildTypeSuggestionHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        suggestions.buildTypeSuggestionHelpers(typeSpec, model, model);
    }

    protected String getParameterSuggestionMethodName(CommandModel classModel, MethodModel method, int index) {
        return suggestions.getParameterSuggestionMethodName(classModel, method, index);
    }

    protected String getResolverParamSuggestionMethodName(CommandModel classModel, ParameterModel parameter) {
        return suggestions.getResolverParamSuggestionMethodName(classModel, parameter);
    }

    // ── Command metadata ──

    protected void buildCommandInfo(TypeSpec.Builder typeSpec, CommandModel model) {
        ClassName commandInfoClass = ClassName.get("io.github.projectunified.craftcommand", "CommandInfo");
        TypeName commandInfoList = ParameterizedTypeName.get(ClassName.get(List.class), commandInfoClass);
        CodeBlock listExpression = commandInfoList(commandInfoEntries(model, commandInfoClass, new ArrayList<>()));

        if (hasDescriptionKey(model)) {
            // i18n descriptions are resolved at runtime, so the accessor builds the list.
            typeSpec.addMethod(MethodSpec.methodBuilder("getCommandInfo")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(commandInfoList)
                    .addStatement("return $L", listExpression)
                    .build());
        } else {
            typeSpec.addField(FieldSpec.builder(commandInfoList, "COMMAND_INFO", Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL)
                    .initializer(listExpression)
                    .build());
            typeSpec.addMethod(MethodSpec.methodBuilder("getCommandInfo")
                    .addAnnotation(Override.class)
                    .addModifiers(Modifier.PUBLIC)
                    .returns(commandInfoList)
                    .addStatement("return COMMAND_INFO")
                    .build());
        }
    }

    private List<CodeBlock> commandInfoEntries(CommandModel model, ClassName commandInfoClass, List<String> parentPath) {
        List<String> currentPath = new ArrayList<>(parentPath);
        currentPath.add(model.getCommandName());

        List<CodeBlock> entries = new ArrayList<>();
        if (model.getDefaultMethod() != null) {
            entries.add(commandInfoEntry(commandInfoClass, currentPath,
                    getUsage(model.getDefaultMethod()), model.getDescription()));
        }
        for (MethodModel sub : model.getSubcommands()) {
            List<String> subPath = new ArrayList<>(currentPath);
            subPath.add(sub.subcommandName());
            entries.add(commandInfoEntry(commandInfoClass, subPath, getUsage(sub), sub.description()));
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            entries.addAll(commandInfoEntries(child, commandInfoClass, currentPath));
        }
        return entries;
    }

    private static CodeBlock commandInfoEntry(ClassName commandInfoClass, List<String> path, String usage, String description) {
        if (isI18nKey(description)) {
            return CodeBlock.of("new $T($L, $S, manager.formatMessage($S, $S))",
                    commandInfoClass, buildPathExpression(path), usage, i18nKey(description), description);
        }
        return CodeBlock.of("new $T($L, $S, $S)", commandInfoClass, buildPathExpression(path), usage, description);
    }

    private static CodeBlock commandInfoList(List<CodeBlock> entries) {
        if (entries.isEmpty()) {
            return CodeBlock.of("$T.emptyList()", Collections.class);
        }
        if (entries.size() == 1) {
            return CodeBlock.of("$T.singletonList($L)", Collections.class, entries.get(0));
        }
        return CodeBlock.builder()
                .add("$T.unmodifiableList($T.asList(\n", Collections.class, Arrays.class)
                .indent()
                .add(CodeBlock.join(entries, ",\n"))
                .unindent()
                .add("\n))")
                .build();
    }

    private static CodeBlock buildPathExpression(List<String> path) {
        if (path.size() == 1) {
            return CodeBlock.of("$T.singletonList($S)", Collections.class, path.get(0));
        }
        return stringList(path);
    }

    private boolean hasDescriptionKey(CommandModel model) {
        if (isI18nKey(model.getDescription())) return true;
        for (MethodModel sub : model.getSubcommands()) {
            if (isI18nKey(sub.description())) return true;
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            if (hasDescriptionKey(child)) return true;
        }
        return false;
    }

    // ── Model queries used by the generators and platforms ──

    public TypeSupport typeSupport() {
        return typeSupport;
    }

    public int getBuiltInWidth(TypeName typeName) {
        return typeSupport.getWidth(typeName);
    }

    public boolean isSenderType(TypeName typeName) {
        return senderTypeRegistry.isSenderType(typeName);
    }

    public boolean isSenderBaseType(TypeName typeName) {
        return senderTypeRegistry.isSenderBaseType(typeName);
    }

    public boolean isSenderParam(TypeName typeName, MethodModel method) {
        if (method != null && typeName.equals(TypeName.get(method.getSenderType()))) return true;
        if (isSenderBaseType(typeName)) return true;
        return isSenderType(typeName);
    }

    protected SenderTypeRegistry senderTypeRegistry() {
        return senderTypeRegistry;
    }

    public boolean firstParamIsSender(ExecutableElement method) {
        if (method.getParameters().isEmpty()) return false;
        return isSenderParam(TypeName.get(method.getParameters().get(0).asType()), null);
    }

    public boolean firstParamIsSender(ExecutableElement resolverMethod, MethodModel commandMethod) {
        if (resolverMethod.getParameters().isEmpty()) return false;
        if (commandMethod != null) {
            TypeName firstParamType = TypeName.get(resolverMethod.getParameters().get(0).asType());
            TypeName commandSenderType = TypeName.get(commandMethod.getSenderType());
            if (firstParamType.equals(commandSenderType)) return true;
        }
        return isSenderParam(TypeName.get(resolverMethod.getParameters().get(0).asType()), commandMethod);
    }

    public int getLocalResolverMinWidth(MethodModel resolverModel, MethodModel commandMethod) {
        int minWidth = 0;
        List<ParameterModel> params = resolverModel.parameters();
        int startIndex = firstParamIsSender(resolverModel.element(), commandMethod) ? 1 : 0;
        for (int i = startIndex; i < params.size(); i++) {
            if (!params.get(i).optional()) {
                minWidth++;
            }
        }
        return minWidth;
    }

    public int getLocalResolverMaxWidth(MethodModel resolverModel, MethodModel commandMethod) {
        List<ParameterModel> params = resolverModel.parameters();
        int startIndex = firstParamIsSender(resolverModel.element(), commandMethod) ? 1 : 0;
        return params.size() - startIndex;
    }

    public CodeBlock getResolverSenderExpression(ExecutableElement localResolver, String rawSourceExpr, String castSenderVar, TypeName commandSenderType) {
        if (localResolver.getParameters().isEmpty()) return CodeBlock.of("$L", castSenderVar);
        TypeName firstParamType = TypeName.get(localResolver.getParameters().get(0).asType());
        if (firstParamType.equals(commandSenderType)) return CodeBlock.of("$L", castSenderVar);
        if (isSenderBaseType(firstParamType)) return CodeBlock.of("$L", rawSourceExpr);
        if (isSenderType(firstParamType)) {
            return CodeBlock.of("$L($L)", "as" + Naming.simpleName(firstParamType), rawSourceExpr);
        }
        return CodeBlock.of("$L", castSenderVar);
    }

    Set<TypeName> getSenderTypesToCast(CommandModel model) {
        Set<TypeName> types = new LinkedHashSet<>();
        collectSenderTypesToCast(model, types);
        return types;
    }

    private void collectSenderTypesToCast(CommandModel model, Set<TypeName> types) {
        if (model.getDefaultMethod() != null) collectSenderTypesToCast(model.getDefaultMethod(), types);
        for (MethodModel sub : model.getSubcommands()) collectSenderTypesToCast(sub, types);
        for (CommandModel child : model.getNestedSubcommands()) collectSenderTypesToCast(child, types);
    }

    private void collectSenderTypesToCast(MethodModel method, Set<TypeName> types) {
        ParameterModel senderParam = method.senderParameter();
        TypeName typeName = TypeName.get(senderParam.type());
        if (!isSenderBaseType(typeName) && senderParam.resolveName() == null) {
            types.add(typeName);
        }
        for (ParameterModel p : method.parameters()) {
            if (p == method.senderParameter()) continue;

            MethodModel resolverModel = p.resolverMethod();
            if (resolverModel != null && !resolverModel.parameters().isEmpty()) {
                TypeName firstParamType = TypeName.get(resolverModel.parameters().get(0).type());
                if (isSenderType(firstParamType) && !isSenderBaseType(firstParamType)) {
                    types.add(firstParamType);
                }
            }

            MethodModel suggestMethod = p.suggestMethod();
            if (suggestMethod != null && !suggestMethod.parameters().isEmpty()) {
                TypeName firstParamType = TypeName.get(suggestMethod.parameters().get(0).type());
                if (isSenderType(firstParamType) && !isSenderBaseType(firstParamType)) {
                    types.add(firstParamType);
                }
            }
        }
    }

    protected CodeBlock buildAliasesExpression(CommandModel model) {
        if (model.getAliases().isEmpty()) {
            return CodeBlock.of("$T.emptyList()", Collections.class);
        }
        if (model.getAliases().size() == 1) {
            return CodeBlock.of("$T.singletonList($S)", Collections.class, model.getAliases().get(0));
        }
        return stringList(model.getAliases());
    }

    static List<String> collectLoweredNames(CommandModel child) {
        List<String> names = new ArrayList<>();
        names.add(child.getCommandName().toLowerCase());
        for (String alias : child.getAliases()) {
            names.add(alias.toLowerCase());
        }
        return names;
    }

    /**
     * Traverses the methods of a command tree.
     */
    protected static boolean anyMethod(CommandModel model, Predicate<ExecutableElement> predicate) {
        if (model.getDefaultMethod() != null && predicate.test(model.getDefaultMethod().element())) return true;
        for (MethodModel sub : model.getSubcommands()) {
            if (predicate.test(sub.element())) return true;
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            if (anyMethod(child, predicate)) return true;
        }
        return false;
    }

    static List<String> collectLoweredNames(MethodModel sub) {
        List<String> names = new ArrayList<>();
        names.add(sub.subcommandName().toLowerCase());
        for (String alias : sub.aliases()) {
            names.add(alias.toLowerCase());
        }
        return names;
    }

    static List<String> subcommandNames(CommandModel model) {
        List<String> names = new ArrayList<>();
        for (CommandModel child : model.getNestedSubcommands()) {
            names.add(child.getCommandName());
            names.addAll(child.getAliases());
        }
        for (MethodModel sub : model.getSubcommands()) {
            names.add(sub.subcommandName());
            names.addAll(sub.aliases());
        }
        return names;
    }

    static CodeBlock stringList(List<String> values) {
        return CodeBlock.of("$T.asList($L)", Arrays.class,
                values.stream().map(v -> CodeBlock.of("$S", v)).collect(CodeBlock.joining(", ")));
    }

    /**
     * Builds an instance method invocation, for example {@code instance.resolve(sender, arg)}.
     */
    public static CodeBlock call(String instance, String method, CodeBlock... args) {
        return CodeBlock.of("$L.$L($L)", instance, method, CodeBlock.join(Arrays.asList(args), ", "));
    }

    /**
     * Starts a {@code switch} case block: emits every label and opens the guarded block, so the generated code
     * reads as hand-written Java with the block indented under its label.
     */
    static void beginCaseBlock(MethodSpec.Builder methodSpec, List<String> names) {
        for (int i = 0; i < names.size() - 1; i++) {
            methodSpec.addCode("case $S:\n", names.get(i));
        }
        methodSpec.beginControlFlow("case $S:", names.get(names.size() - 1));
    }

    // ── Shared helpers ──

    String getUsage(MethodModel method) {
        StringJoiner usage = new StringJoiner(" ");
        for (ParameterModel p : method.parameters()) {
            MethodModel resolverModel = p.resolverMethod();
            if (resolverModel == null) {
                usage.add(usageToken(p));
                continue;
            }
            for (ParameterModel rp : resolverModel.parameters()) {
                if (!isSenderParam(TypeName.get(rp.type()), method)) {
                    usage.add(usageToken(rp));
                }
            }
        }
        return usage.toString();
    }

    private static String usageToken(ParameterModel p) {
        return p.optional() ? "[" + p.name() + "]" : "<" + p.name() + ">";
    }

    protected static String getSubcommandNames(CommandModel model) {
        List<String> list = new ArrayList<>();
        for (CommandModel child : model.getNestedSubcommands()) {
            list.add(child.getCommandName());
        }
        for (MethodModel sub : model.getSubcommands()) {
            list.add(sub.subcommandName());
        }
        return list.toString();
    }

    /**
     * Emits a {@code throw new CommandException(manager.formatMessage(...))} statement.
     */
    static void addCommandError(MethodSpec.Builder spec, String key, String defaultMessage, String argument) {
        spec.addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                CommandException.class, key, defaultMessage, argument);
    }

    protected static boolean isI18nKey(String message) {
        return message.startsWith("i18n:");
    }

    protected static String i18nKey(String message) {
        return message.substring(5);
    }

    // ── SPI dispatch ──

    List<ParameterAnnotationHandler<?>> parameterHandlers() {
        return parameterHandlers;
    }

    List<MethodAnnotationHandler<?>> methodHandlers() {
        return methodHandlers;
    }

    /**
     * Runs the SPI parameter handlers of a resolved parameter.
     */
    public void runParameterAnnotationHandlers(ParameterModel param, String varName, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec) {
        execution.runParameterAnnotationHandlers(param, varName, instanceExpr, senderVar, methodSpec);
    }

    /**
     * Emits the call to a resolver method that produces a parameter value.
     */
    public void generateResolverInvocation(MethodSpec.Builder methodSpec, MethodModel resolverModel, CommandModel rootModel, TypeName pTypeName, String varName, CodeBlock senderExpr, List<String> resolverArgVarNames, boolean includeSender) {
        execution.generateResolverInvocation(methodSpec, resolverModel, rootModel, pTypeName, varName, senderExpr, resolverArgVarNames, includeSender);
    }
}
