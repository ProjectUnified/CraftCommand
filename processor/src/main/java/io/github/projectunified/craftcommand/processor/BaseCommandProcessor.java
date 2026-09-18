package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.processor.extension.MethodAnnotationHandler;
import io.github.projectunified.craftcommand.processor.extension.ParameterAnnotationHandler;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;
import io.github.projectunified.craftcommand.processor.parser.CommandParser;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.*;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.*;

/**
 * Lightweight, human-readable base annotation processor for CraftCommand.
 * Supports custom validation annotations, parameter resolvers, deep subcommand nesting, and SPI extensions.
 */
public abstract class BaseCommandProcessor extends AbstractProcessor {
    private final SuggestionGenerator suggestions = new SuggestionGenerator(this);
    private final ExecutionGenerator execution = new ExecutionGenerator(this);


    protected final TypeSupport typeSupport = TypeSupport.builtins();
    private final List<ParameterAnnotationHandler<?>> parameterHandlers = new ArrayList<>();
    private final List<MethodAnnotationHandler<?>> methodHandlers = new ArrayList<>();
    private final SenderTypeRegistry senderTypeRegistry = new SenderTypeRegistry();

    protected static String getUsage(MethodModel method) {
        StringBuilder sb = new StringBuilder();
        for (ParameterModel p : method.getParameters()) {
            if (p == method.getSenderParameter()) continue;

            MethodModel resolverModel = p.getResolverMethod();
            if (resolverModel != null) {
                for (ParameterModel rp : resolverModel.getParameters()) {
                    if (resolverModel.getSenderParameter() != null && rp == resolverModel.getSenderParameter()) {
                        continue;
                    }
                    if (rp.isOptional()) {
                        sb.append("[").append(rp.getName()).append("] ");
                    } else {
                        sb.append("<").append(rp.getName()).append("> ");
                    }
                }
                continue;
            }

            if (p.isOptional()) {
                sb.append("[").append(p.getName()).append("] ");
            } else {
                sb.append("<").append(p.getName()).append("> ");
            }
        }
        return sb.toString().trim();
    }

    // ── Static Utilities ──

    public static String getSimpleName(TypeName typeName) {
        return Naming.simpleName(typeName);
    }

    protected static String getSubcommandNames(CommandModel model) {
        List<String> list = new ArrayList<>();
        for (CommandModel child : model.getNestedSubcommands()) {
            list.add(child.getCommandName());
        }
        for (MethodModel sub : model.getSubcommands()) {
            list.add(sub.getSubcommandName());
        }
        return list.toString();
    }

    protected static boolean isI18nKey(String message) {
        return message.startsWith("i18n:");
    }

    protected static String i18nKey(String message) {
        return message.substring(5);
    }

    /**
     * Resolves the resolver declared by a parameter of a resolver, which is not part of the resolver set of the
     * enclosing command class.
     */
    static ExecutableElement resolveLocalResolver(ParameterModel p, CommandModel classModel) {
        String resolveName = p.getResolveName();
        if (resolveName == null || resolveName.isEmpty()) return null;
        return ResolverLookup.findMethod(classModel.getElement(), resolveName);
    }

    // ── Processor Lifecycle ──

    public TypeSupport typeSupport() {
        return typeSupport;
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        loadExtensions();
        registerTypes(typeSupport);
    }

    private void loadExtensions() {
        parameterHandlers.clear();
        methodHandlers.clear();
        ClassLoader cl = getClass().getClassLoader();
        parameterHandlers.addAll(SpiLoader.loadParameterHandlers(cl));
        methodHandlers.addAll(SpiLoader.loadMethodHandlers(cl));
    }

    // ── Platform Customization Hooks ──

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Command.class)) {
            if (element instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) element;
                Element enclosing = typeElement.getEnclosingElement();
                if (enclosing instanceof TypeElement && CommandPrism.isPresent(enclosing)) {
                    continue;
                }
                CommandModel commandModel = CommandParser.parse(typeElement, processingEnv);
                if (commandModel != null) {
                    try {
                        buildWrapperClass(commandModel, typeElement);
                    } catch (IOException e) {
                        if (!e.getClass().getName().contains("FilerException")) {
                            processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                                    "Failed to generate wrapper: " + e.getMessage(), typeElement);
                        }
                    }
                }
            }
        }
        return true;
    }

    protected void registerTypes(TypeSupport types) {
    }

    /**
     * The SPI handlers that augment parameter code generation.
     */
    List<ParameterAnnotationHandler<?>> parameterHandlers() {
        return parameterHandlers;
    }

    /**
     * Runs the SPI parameter handlers of a resolved parameter, delegating to the execution generator.
     *
     * @param param        the parameter that was resolved
     * @param varName      the generated local holding its value
     * @param instanceExpr the command instance expression
     * @param senderVar    the local holding the command sender
     * @param methodSpec   the method being generated
     */
    public void runParameterAnnotationHandlers(ParameterModel param, String varName, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec) {
        execution.runParameterAnnotationHandlers(param, varName, instanceExpr, senderVar, methodSpec);
    }

    /**
     * Emits the call to a resolver method that produces a parameter value, delegating to the execution
     * generator.
     *
     * @param methodSpec           the method being generated
     * @param resolverModel        the resolver method
     * @param rootModel            the root command model
     * @param pTypeName            the resolved parameter type
     * @param varName              the local receiving the resolved value
     * @param senderVarName        the local holding the command sender
     * @param resolverArgVarNames  the locals holding the resolver arguments
     * @param includeSender        whether the sender is passed to the resolver
     */
    public void generateResolverInvocation(MethodSpec.Builder methodSpec, MethodModel resolverModel, CommandModel rootModel, TypeName pTypeName, String varName, String senderVarName, List<String> resolverArgVarNames, boolean includeSender) {
        execution.generateResolverInvocation(methodSpec, resolverModel, rootModel, pTypeName, varName, senderVarName, resolverArgVarNames, includeSender);
    }

    /**
     * The SPI handlers that augment method code generation.
     */
    List<MethodAnnotationHandler<?>> methodHandlers() {
        return methodHandlers;
    }

    protected abstract String getWrapperClassSuffix();

    protected abstract ClassName getSenderTypeName();

    protected abstract TypeName getManagerType();

    protected void configureClass(TypeSpec.Builder typeSpec, CommandModel model) {
    }

    protected void addPlatformFields(TypeSpec.Builder typeSpec, CommandModel model) {
    }

    protected void addConstructorStatements(MethodSpec.Builder constructorBuilder, CommandModel model) {
    }

    protected abstract void generateEntryMethods(TypeSpec.Builder typeSpec, CommandModel model, TypeElement typeElement);

    protected void generatePlatformHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
    }

    protected void onBeforeExecute(MethodSpec.Builder methodSpec, Element element, String returnStatement) {
    }

    protected void generateUnknownSubcommandMessage(MethodSpec.Builder methodSpec, CommandModel model) {
        methodSpec.addStatement("System.out.println($S)", "Unknown subcommand. Available: " + getSubcommandNames(model));
    }

    // ── Class Generation Orchestrator ──

    protected CodeBlock getSenderExpression(String senderVar) {
        return CodeBlock.of("$L", senderVar);
    }

    protected void buildWrapperClass(CommandModel model, TypeElement typeElement) throws IOException {
        String wrapperClassName = model.getClassName().simpleName() + getWrapperClassSuffix();
        TypeName genericCommandManager = getManagerType();

        TypeSpec.Builder typeSpec = TypeSpec.classBuilder(wrapperClassName)
                .addJavadoc("Command wrapper class for {@link $T}.\n"
                        + "Generated automatically by the annotation processor.\n"
                        + "Do not modify this class directly.\n", model.getClassName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);

        // 1. Configure class interfaces / superclass
        typeSpec.addSuperinterface(ClassName.get("io.github.projectunified.craftcommand", "BaseCommand"));
        configureClass(typeSpec, model);

        // 2. Fields
        typeSpec.addField(FieldSpec.builder(model.getClassName(), "instance", Modifier.PRIVATE, Modifier.FINAL)
                .addJavadoc("The underlying command instance.\n")
                .build());
        typeSpec.addField(FieldSpec.builder(genericCommandManager, "manager", Modifier.PRIVATE, Modifier.FINAL)
                .addJavadoc("The command manager used to resolve parameters and handle errors.\n")
                .build());
        addPlatformFields(typeSpec, model);

        // 3. Constructor
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addJavadoc("Constructs a new command wrapper.\n\n"
                        + "@param instance the command instance\n"
                        + "@param manager the command manager\n")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(model.getClassName(), "instance")
                .addParameter(genericCommandManager, "manager");
        addConstructorStatements(constructorBuilder, model);
        constructorBuilder.addStatement("this.instance = instance")
                .addStatement("this.manager = manager");
        generateSubcommandFieldsAndConstructors(model, typeSpec, constructorBuilder, "instance");
        typeSpec.addMethod(constructorBuilder.build());

        // 4. Platform Entry Methods (execute, tabComplete, getCommandNode, etc.)
        generateEntryMethods(typeSpec, model, typeElement);

        // 5. Helpers & Execution Routing
        generateHelpers(typeSpec, model);

        // 6. CommandInfo Metadata
        buildCommandInfo(typeSpec, model);

        JavaFile javaFile = JavaFile.builder(model.getPackageName(), typeSpec.build())
                .skipJavaLangImports(true)
                .build();
        javaFile.writeTo(processingEnv.getFiler());
    }

    // ── Nested Subcommands ──

    protected void generateHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        generateSubcommandClassExecutors(typeSpec, model, model);
        suggestions.buildParameterSuggestions(typeSpec, model, model);
        buildSenderCastHelpers(typeSpec, model);
        suggestions.buildBooleanSuggestionHelper(typeSpec, model);
        generatePlatformHelpers(typeSpec, model);
    }

    protected void generateSubcommandFieldsAndConstructors(CommandModel model, TypeSpec.Builder typeSpec, MethodSpec.Builder constructor, String parentFieldName) {
        for (CommandModel child : model.getNestedSubcommands()) {
            String fieldName = getSubcommandFieldName(child);
            typeSpec.addField(child.getClassName(), fieldName, Modifier.PRIVATE, Modifier.FINAL);
            boolean isStatic = child.getElement().getModifiers().contains(Modifier.STATIC);
            if (isStatic) {
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

    // ── Array Execution Routing ──

    public String getResolverInstanceExpr(MethodModel resolver, CommandModel rootModel) {
        TypeElement resolverClass = (TypeElement) resolver.getElement().getEnclosingElement();
        CommandModel resolverModel = rootModel.findModel(resolverClass);
        if (resolverModel != null) {
            return getInstanceVarExpression(resolverModel, rootModel);
        }
        return null;
    }

    public void generateExecuteMethodBody(MethodSpec.Builder executeSpec, CommandModel model, String returnStatement) {
        executeSpec.beginControlFlow("try");
        execution.buildExecutionRouting(executeSpec, model, "args", "instance", model, returnStatement);
        executeSpec.nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("manager.getErrorHandler().accept(sender, e)")
                .endControlFlow();
        executeSpec.addStatement("$L", returnStatement);
    }

    protected void generateSubcommandClassExecutors(TypeSpec.Builder typeSpec, CommandModel model, CommandModel rootModel) {
        for (CommandModel child : model.getNestedSubcommands()) {
            String helperMethodName = Naming.executeHelper(child.getClassName());
            MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(helperMethodName)
                    .addJavadoc("Routes and executes the subcommand represented by the nested class {@link $T}.\n\n"
                            + "@param sender the command sender\n"
                            + "@param args the arguments for the subcommand\n"
                            + "@throws Exception if any error occurs during execution\n", child.getClassName())
                    .addModifiers(Modifier.PRIVATE)
                    .addException(Exception.class)
                    .addParameter(getSenderTypeName(), "sender")
                    .addParameter(String[].class, "args");

            String childInstanceVar = "this." + getSubcommandFieldName(child);
            execution.buildExecutionRouting(methodSpec, child, "args", childInstanceVar, rootModel, "return");
            typeSpec.addMethod(methodSpec.build());

            // Tab suggest helper method for this child subcommand class
            String suggestHelperMethodName = Naming.suggestHelper(child.getClassName());
            MethodSpec.Builder suggestMethodSpec = MethodSpec.methodBuilder(suggestHelperMethodName)
                    .addJavadoc("Retrieves suggestions for the nested subcommand class {@link $T}.\n\n"
                            + "@param sender the command sender\n"
                            + "@param args the command arguments\n"
                            + "@return a list of suggestions\n", child.getClassName())
                    .addModifiers(Modifier.PRIVATE)
                    .returns(ParameterizedTypeName.get(List.class, String.class))
                    .addParameter(getSenderTypeName(), "sender")
                    .addParameter(String[].class, "args");

            buildSuggestionRouting(suggestMethodSpec, child, "args", childInstanceVar, rootModel);
            typeSpec.addMethod(suggestMethodSpec.build());

            // Recursively generate for grandchildren
            generateSubcommandClassExecutors(typeSpec, child, rootModel);
        }
    }














    // ── Helper Generators (Booleans & Casts) ──



    // ── Command Metadata (CommandInfo) ──

    /**
     * Reports a {@code @Suggest} provider that names neither a field nor a method of the command class.
     */
    void reportUnknownSuggestProvider(String provider, CommandModel classModel) {
        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                "Could not find field or method '" + provider + "' for suggestions in " + classModel.getClassName().simpleName());
    }

    /**
     * Generates tab completion for a command class, delegating to the suggestion generator.
     *
     * @param methodSpec  the entry method being generated
     * @param model       the command class
     * @param argsVar     the argument array in scope
     * @param instanceVar the command instance expression
     * @param rootModel   the root command model
     */
    protected void buildSuggestionRouting(MethodSpec.Builder methodSpec, CommandModel model, String argsVar, String instanceVar, CommandModel rootModel) {
        suggestions.buildSuggestionRouting(methodSpec, model, argsVar, instanceVar, rootModel);
    }

    /**
     * Generates the sender cast helpers a command class needs, delegating to the suggestion generator.
     *
     * @param typeSpec the wrapper class being generated
     * @param model    the command class
     */
    protected void buildSenderCastHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        suggestions.buildSenderCastHelpers(typeSpec, model);
    }

    /**
     * The generated helper name for a parameter's suggestions.
     */
    protected String getParameterSuggestionMethodName(CommandModel classModel, MethodModel method, int index) {
        return suggestions.getParameterSuggestionMethodName(classModel, method, index);
    }

    /**
     * The generated helper name for a resolver parameter's suggestions.
     */
    protected String getResolverParamSuggestionMethodName(CommandModel classModel, MethodModel method, String resolverName, int index) {
        return suggestions.getResolverParamSuggestionMethodName(classModel, method, resolverName, index);
    }

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

    /**
     * Flattens a command tree into its {@code CommandInfo} entries, parents before children.
     */
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
            subPath.add(sub.getSubcommandName());
            entries.add(commandInfoEntry(commandInfoClass, subPath, getUsage(sub), sub.getDescription()));
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

    /**
     * Wraps the command info entries into an immutable list expression, one entry per line.
     */
    private static CodeBlock commandInfoList(List<CodeBlock> entries) {
        if (entries.isEmpty()) {
            return CodeBlock.of("$T.emptyList()", Collections.class);
        }
        CodeBlock.Builder list = CodeBlock.builder()
                .add("$T.unmodifiableList($T.asList(\n", Collections.class, Arrays.class)
                .indent();
        for (int i = 0; i < entries.size(); i++) {
            if (i > 0) list.add(",\n");
            list.add(entries.get(i));
        }
        return list.unindent().add("\n))").build();
    }

    private static CodeBlock buildPathExpression(List<String> path) {
        if (path.size() == 1) {
            return CodeBlock.of("$T.singletonList($S)", Collections.class, path.get(0));
        }
        CodeBlock.Builder b = CodeBlock.builder().add("$T.asList(", Arrays.class);
        for (int i = 0; i < path.size(); i++) {
            if (i > 0) b.add(", ");
            b.add("$S", path.get(i));
        }
        return b.add(")").build();
    }

    private boolean hasDescriptionKey(CommandModel model) {
        if (isI18nKey(model.getDescription())) return true;
        for (MethodModel sub : model.getSubcommands()) {
            if (isI18nKey(sub.getDescription())) return true;
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            if (hasDescriptionKey(child)) return true;
        }
        return false;
    }

    // ── Model and Lookup Utilities ──

    protected boolean isField(TypeElement typeElement, String name) {
        return ResolverLookup.isField(typeElement, name);
    }

    public int getBuiltInWidth(TypeName typeName) {
        return typeSupport.getWidth(typeName);
    }

    public boolean isPlatformBuiltInType(TypeName typeName) {
        TypeSupport.Entry e = typeSupport.get(typeName);
        return e != null && (e.platformMultiResolution != null || e.platformSuggestions != null);
    }

    public boolean isSenderType(TypeName typeName) {
        return senderTypeRegistry.isSenderType(typeName);
    }

    public boolean isSenderBaseType(TypeName typeName) {
        return senderTypeRegistry.isSenderBaseType(typeName);
    }

    public boolean isSenderParam(TypeName typeName, MethodModel method) {
        if (method != null && typeName.toString().equals(TypeName.get(method.getSenderType()).toString())) return true;
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
            if (firstParamType.toString().equals(commandSenderType.toString())) return true;
        }
        return isSenderParam(TypeName.get(resolverMethod.getParameters().get(0).asType()), commandMethod);
    }

    public int getLocalResolverMinWidth(MethodModel resolverModel, MethodModel commandMethod) {
        int minWidth = 0;
        List<ParameterModel> params = resolverModel.getParameters();
        int startIndex = firstParamIsSender(resolverModel.getElement(), commandMethod) ? 1 : 0;
        for (int i = startIndex; i < params.size(); i++) {
            if (!params.get(i).isOptional()) {
                minWidth++;
            }
        }
        return minWidth;
    }

    public int getLocalResolverMaxWidth(MethodModel resolverModel, MethodModel commandMethod) {
        List<ParameterModel> params = resolverModel.getParameters();
        int startIndex = firstParamIsSender(resolverModel.getElement(), commandMethod) ? 1 : 0;
        return params.size() - startIndex;
    }

    public String getResolverSenderExpression(ExecutableElement localResolver, String rawSourceExpr, String castSenderVar, TypeName commandSenderType) {
        if (localResolver.getParameters().isEmpty()) return castSenderVar;
        TypeName firstParamType = TypeName.get(localResolver.getParameters().get(0).asType());
        if (firstParamType.toString().equals(commandSenderType.toString())) return castSenderVar;
        if (isSenderBaseType(firstParamType)) return rawSourceExpr;
        if (isSenderType(firstParamType)) return "as" + getSimpleName(firstParamType) + "(" + rawSourceExpr + ")";
        return castSenderVar;
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
        ParameterModel senderParam = method.getSenderParameter();
        TypeName typeName = TypeName.get(senderParam.getType());
        if (!isSenderBaseType(typeName) && senderParam.getResolveName() == null) {
            types.add(typeName);
        }
        for (ParameterModel p : method.getParameters()) {
            if (p == method.getSenderParameter()) continue;

            MethodModel resolverModel = p.getResolverMethod();
            if (resolverModel != null && !resolverModel.getParameters().isEmpty()) {
                TypeName firstParamType = TypeName.get(resolverModel.getParameters().get(0).getType());
                if (isSenderType(firstParamType) && !isSenderBaseType(firstParamType)) {
                    types.add(firstParamType);
                }
            }

            MethodModel suggestMethod = p.getSuggestMethod();
            if (suggestMethod != null && !suggestMethod.getParameters().isEmpty()) {
                TypeName firstParamType = TypeName.get(suggestMethod.getParameters().get(0).getType());
                if (isSenderType(firstParamType) && !isSenderBaseType(firstParamType)) {
                    types.add(firstParamType);
                }
            }
        }
    }

    boolean hasBooleanParameter(CommandModel model) {
        if (model.getDefaultMethod() != null && hasBooleanParameter(model.getDefaultMethod())) return true;
        for (MethodModel sub : model.getSubcommands()) {
            if (hasBooleanParameter(sub)) return true;
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            if (hasBooleanParameter(child)) return true;
        }
        return false;
    }

    boolean hasBooleanParameter(MethodModel method) {
        for (ParameterModel p : method.getParameters()) {
            TypeName typeName = TypeName.get(p.getType());
            if (typeName.toString().equals("boolean") || typeName.toString().equals("java.lang.Boolean")) {
                return true;
            }
        }
        return false;
    }

    protected CodeBlock buildAliasesExpression(CommandModel model) {
        if (model.getAliases().isEmpty()) {
            return CodeBlock.of("$T.emptyList()", Collections.class);
        }
        if (model.getAliases().size() == 1) {
            return CodeBlock.of("$T.singletonList($S)", Collections.class, model.getAliases().get(0));
        }
        CodeBlock.Builder aliasesBlock = CodeBlock.builder().add("$T.asList(", Arrays.class);
        for (int i = 0; i < model.getAliases().size(); i++) {
            aliasesBlock.add("$S", model.getAliases().get(i));
            if (i < model.getAliases().size() - 1) {
                aliasesBlock.add(", ");
            }
        }
        aliasesBlock.add(")");
        return aliasesBlock.build();
    }

    List<String> collectLoweredNames(CommandModel child) {
        List<String> names = new ArrayList<>();
        names.add(child.getCommandName().toLowerCase());
        for (String alias : child.getAliases()) {
            names.add(alias.toLowerCase());
        }
        return names;
    }

    static List<String> collectLoweredNames(MethodModel sub) {
        List<String> names = new ArrayList<>();
        names.add(sub.getSubcommandName().toLowerCase());
        for (String alias : sub.getAliases()) {
            names.add(alias.toLowerCase());
        }
        return names;
    }

    /**
     * Collects the names and aliases suggested for a command and its subcommands.
     */
    static List<String> subcommandNames(CommandModel model) {
        List<String> names = new ArrayList<>();
        for (CommandModel child : model.getNestedSubcommands()) {
            names.add(child.getCommandName());
            names.addAll(child.getAliases());
        }
        for (MethodModel sub : model.getSubcommands()) {
            names.add(sub.getSubcommandName());
            names.addAll(sub.getAliases());
        }
        return names;
    }

    /**
     * Renders string literals as a list expression.
     */
    static CodeBlock stringList(List<String> values) {
        CodeBlock.Builder list = CodeBlock.builder().add("$T.asList(", Arrays.class);
        for (int i = 0; i < values.size(); i++) {
            if (i > 0) list.add(", ");
            list.add("$S", values.get(i));
        }
        return list.add(")").build();
    }

    /**
     * Starts a {@code switch} case block: emits every label and opens the guarded block, so the generated code
     * reads as hand-written Java with the block indented under its label.
     *
     * @param methodSpec the method being generated
     * @param names      all case labels, in declaration order
     */
    static void beginCaseBlock(MethodSpec.Builder methodSpec, List<String> names) {
        for (int i = 0; i < names.size() - 1; i++) {
            methodSpec.addCode("case $S:\n", names.get(i));
        }
        methodSpec.beginControlFlow("case $S:", names.get(names.size() - 1));
    }

    boolean isStringArray(TypeMirror type) {
        if (type.getKind() != TypeKind.ARRAY) return false;
        ArrayType arrayType = (ArrayType) type;
        TypeMirror componentType = arrayType.getComponentType();
        if (componentType.getKind() != TypeKind.DECLARED) return false;
        TypeElement componentElement = (TypeElement) ((DeclaredType) componentType).asElement();
        return componentElement.getQualifiedName().toString().equals("java.lang.String");
    }
}

