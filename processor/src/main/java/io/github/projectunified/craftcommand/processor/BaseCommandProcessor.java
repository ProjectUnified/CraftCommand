package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Optional;
import io.github.projectunified.craftcommand.annotation.Resolve;
import io.github.projectunified.craftcommand.exception.CommandException;
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
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * Re-structured, highly readable base class for command annotation processors.
 * Supports custom validation annotations, parameter resolvers, deep subcommand nesting, and compiler extensions via SPI.
 */
public abstract class BaseCommandProcessor extends AbstractProcessor {

    /**
     * Handlers for parameter-level validation and extension annotations loaded via SPI.
     */
    private final List<ParameterAnnotationHandler<?>> parameterHandlers = new ArrayList<>();

    /**
     * Handlers for method-level execution and extension annotations loaded via SPI.
     */
    private final List<MethodAnnotationHandler<?>> methodHandlers = new ArrayList<>();

    /**
     * Compile-time resolver/suggest/field lookups. Initialized in {@link #init}.
     */
    private ResolverLookup resolverLookup;

    /**
     * Registry of built-in and platform parameter types. Replaces the four
     * scattered {@code if (name.equals("int") ...)} chains. Platforms register
     * their types here via {@link TypeSupport#register(TypeSupport.Entry)}.
     */
    private final TypeSupport typeSupport = TypeSupport.builtins();

    /**
     * @return the type support registry. Platform processors use this to
     * register platform-specific types (Player, World, Location, ...).
     */
    protected TypeSupport typeSupport() {
        return typeSupport;
    }

    /**
     * Utility method to get the simple name of a type.
     *
     * @param typeName the full type name
     * @return the simple type name
     */
    public static String getSimpleName(TypeName typeName) {
        return Naming.simpleName(typeName);
    }

    /**
     * Gets the default literal value expression for a primitive (or wrapper) type.
     * Delegates to {@link TypeSupport}; returns {@code "null"} for non-primitives.
     *
     * @param type the type mirror
     * @return the default value literal string
     */
    public String getDefaultPrimitiveValue(TypeMirror type) {
        return typeSupport.primitiveDefault(TypeName.get(type));
    }

    // ── Platform-Specific Configuration Hooks ──

    /**
     * Utility method to format a list of all subcommand names defined in a command model.
     *
     * @param model the command model
     * @return a formatted string of subcommand names
     */
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

    /**
     * Utility method to build the command usage syntax string for a command method.
     *
     * @param method the method model
     * @return the usage string
     */
    protected static String getUsage(MethodModel method) {
        StringBuilder sb = new StringBuilder();
        if (!method.isDefault()) {
            sb.append(method.getSubcommandName()).append(" ");
        }
        for (ParameterModel p : method.getParameters()) {
            if (p.isOptional()) {
                sb.append("[").append(p.getName()).append("] ");
            } else {
                sb.append("<").append(p.getName()).append("> ");
            }
        }
        return sb.toString().trim();
    }

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.resolverLookup = new ResolverLookup(processingEnv);
        loadExtensions();
    }

    /**
     * Loads validation and execution processor extension handlers via SPI.
     */
    private void loadExtensions() {
        parameterHandlers.clear();
        methodHandlers.clear();
        ClassLoader cl = getClass().getClassLoader();
        parameterHandlers.addAll(SpiLoader.loadParameterHandlers(cl));
        methodHandlers.addAll(SpiLoader.loadMethodHandlers(cl));
    }

    /**
     * Returns the wrapper class suffix specific to the platform.
     *
     * @return the class name suffix (e.g. "_Executor" or "_Standalone")
     */
    protected abstract String getWrapperClassSuffix();

    /**
     * Configures the super interfaces/classes that the generated wrapper should implement/extend.
     *
     * @param typeSpec the class definition builder
     */
    protected abstract void configureSuperType(TypeSpec.Builder typeSpec);

    /**
     * Returns the platform command interface/superclass that the wrapper implements/extends
     * (e.g. {@code PaperCommand}, {@code StandaloneCommand}, {@code org.bukkit.command.Command}).
     * Used as the return type of the generated {@code factory(...)} method.
     */
    protected abstract TypeName getCommandInterfaceType();

    // ── Platform-Specific Execution Hooks ──

    /**
     * Returns the platform-specific command sender type name (e.g. Bukkit CommandSender or Object).
     */
    protected abstract ClassName getSenderTypeName();

    /**
     * Returns the platform-specific command manager type.
     */
    protected abstract TypeName getManagerType();

    /**
     * Generates platform-specific main wrapper entry methods (e.g. onCommand or execute).
     */
    protected abstract void buildEntryMethods(TypeSpec.Builder typeSpec, CommandModel model, TypeElement typeElement);

    /**
     * Configures the constructor for platform-specific statements (e.g., registering command).
     */
    protected void configureConstructor(MethodSpec.Builder constructorBuilder, CommandModel model) {
        // Default: no-op
    }

    // ── Platform-Specific Suggestion Hooks ──

    /**
     * Hook run prior to execution routing of a subcommand or command method.
     */
    protected void onBeforeExecute(MethodSpec.Builder methodSpec, Element element, String returnStatement) {
        // Default: no-op
    }

    /**
     * Generates statements to handle cases where an unknown subcommand is typed.
     */
    protected void generateUnknownSubcommandMessage(MethodSpec.Builder methodSpec, CommandModel model) {
        methodSpec.addStatement("System.out.println($S)", "Unknown subcommand. Available: " + getSubcommandNames(model));
    }

    // ── Recursive Fields and Subcommands Generation ──

    /**
     * Checks if the sender type is supported
     */
    protected boolean isSenderType(TypeName typeName) {
        return isSenderBaseType(typeName);
    }

    protected boolean isSenderBaseType(TypeName typeName) {
        return typeName.toString().equals("java.lang.Object");
    }

    // ── Helper Naming Utilities ──

    /**
     * Helper statement generator to cast general Object sender to platform-specific sender.
     */
    protected void generateDefaultSenderCastForSuggestion(MethodSpec.Builder methodSpec) {
        if (getSenderTypeName().toString().equals("java.lang.Object")) {
            methodSpec.addStatement("$T senderCast = sender", getSenderTypeName());
        } else {
            methodSpec.addStatement("$T senderCast = ($T) sender", getSenderTypeName(), getSenderTypeName());
        }
    }

    /**
     * Main entry point for the compiler annotation processing round.
     * Searches for types annotated with {@code @Command}, parses their structure, and generates the wrappers.
     */
    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        // Iterate through all classes annotated with @Command
        for (Element element : roundEnv.getElementsAnnotatedWith(Command.class)) {
            if (element instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) element;
                // Parse element tree into CommandModel
                CommandModel commandModel = CommandParser.parse(typeElement, processingEnv);
                if (commandModel != null) {
                    try {
                        // Generate Java wrapper class using JavaPoet
                        buildWrapperClass(commandModel, typeElement);
                    } catch (IOException e) {
                        processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Failed to generate wrapper: " + e.getMessage(), typeElement);
                    }
                }
            }
        }
        return true;
    }

    /**
     * Generates the wrapper class for the command model using JavaPoet.
     * Setting up fields, constructor, entry routing methods, sub-executors, and custom resolvers.
     */
    protected void buildWrapperClass(CommandModel model, TypeElement typeElement) throws IOException {
        String wrapperClassName = model.getClassName().simpleName() + getWrapperClassSuffix();
        TypeName genericCommandManager = getManagerType();

        // 1. Define class wrapper definition
        TypeSpec.Builder typeSpec = TypeSpec.classBuilder(wrapperClassName)
                .addJavadoc("Command wrapper class for {@link $T}.\n"
                        + "Generated automatically by the annotation processor.\n"
                        + "Do not modify this class directly.\n", model.getClassName())
                .addModifiers(Modifier.PUBLIC, Modifier.FINAL);
        configureSuperType(typeSpec);
        typeSpec.addSuperinterface(ClassName.get("io.github.projectunified.craftcommand", "CommandInfoExposer"));

        // Fields
        typeSpec.addField(FieldSpec.builder(model.getClassName(), "instance", Modifier.PRIVATE, Modifier.FINAL)
                .addJavadoc("The underlying command instance.\n")
                .build());
        typeSpec.addField(FieldSpec.builder(genericCommandManager, "manager", Modifier.PRIVATE, Modifier.FINAL)
                .addJavadoc("The command manager used to resolve parameters and handle errors.\n")
                .build());

        // Constructor Builder
        MethodSpec.Builder constructorBuilder = MethodSpec.constructorBuilder()
                .addJavadoc("Constructs a new command wrapper.\n\n"
                        + "@param instance the command instance\n"
                        + "@param manager the command manager\n")
                .addModifiers(Modifier.PUBLIC)
                .addParameter(model.getClassName(), "instance")
                .addParameter(genericCommandManager, "manager");
        configureConstructor(constructorBuilder, model);
        constructorBuilder.addStatement("this.instance = instance")
                .addStatement("this.manager = manager");

        // Generate nested subcommands fields and construction statements recursively
        generateFieldsAndConstructorStatements(model, typeSpec, constructorBuilder, "instance");

        typeSpec.addMethod(constructorBuilder.build());

        // Platform-specific entry methods (execute/tabComplete or onCommand/onTabComplete)
        buildEntryMethods(typeSpec, model, typeElement);

        // Generate subcommand execution and completion helpers recursively
        generateSubcommandClassExecutors(typeSpec, model, model);

        // Helper filterSuggestions
        typeSpec.addMethod(MethodSpec.methodBuilder("filterSuggestions")
                .addJavadoc("Filters the list of suggestions by checking if they start with the current input (case-insensitive).\n\n"
                        + "@param suggestions the raw list of suggestions\n"
                        + "@param current the current user input to filter by\n"
                        + "@return the filtered list of suggestions\n")
                .addModifiers(Modifier.PRIVATE)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addParameter(ParameterizedTypeName.get(List.class, String.class), "suggestions")
                .addParameter(String.class, "current")
                .addStatement("if (suggestions == null) return $T.emptyList()", Collections.class)
                .addStatement("$T<$T> result = new $T<>()", List.class, String.class, ArrayList.class)
                .addStatement("String lower = current.toLowerCase()")
                .beginControlFlow("for ($T s : suggestions)", String.class)
                .beginControlFlow("if (s.toLowerCase().startsWith(lower))")
                .addStatement("result.add(s)")
                .endControlFlow()
                .endControlFlow()
                .addStatement("return result")
                .build());

        for (TypeName type : getDynamicResolverTypes(model)) {
            String methodName = getResolverMethodName(type);

            MethodSpec.Builder mb = MethodSpec.methodBuilder(methodName)
                    .addJavadoc("Resolves a parameter of type {@link $T} using the manager's registered argument resolver.\n\n"
                            + "@param sender the command sender\n"
                            + "@param args the full command arguments array\n"
                            + "@param paramName the name of the parameter for error message generation\n"
                            + "@param indexHolder a single-element array holding the current argument index\n"
                            + "@param optional whether the parameter is optional\n"
                            + "@param defaultValue the default value string if optional\n"
                            + "@return the resolved value of type {@code $T}\n"
                            + "@throws Exception if resolution fails or required arguments are missing\n", type, type)
                    .addAnnotation(AnnotationSpec.builder(SuppressWarnings.class)
                            .addMember("value", "$S", "unchecked")
                            .build())
                    .addModifiers(Modifier.PRIVATE)
                    .returns(type)
                    .addException(Exception.class)
                    .addParameter(getSenderTypeName(), "sender")
                    .addParameter(String[].class, "args")
                    .addParameter(String.class, "paramName")
                    .addParameter(int[].class, "indexHolder")
                    .addParameter(TypeName.BOOLEAN, "optional")
                    .addParameter(String.class, "defaultValue");

            mb.addStatement("return manager.resolveParameter(sender, $T.class, args, indexHolder, paramName, optional, defaultValue)", type);
            typeSpec.addMethod(mb.build());
        }

        // Generate parameter suggestions helpers recursively
        buildParameterSuggestions(typeSpec, model, model);

        // Generate additional shared helper methods (e.g. for boolean suggestions)
        buildAdditionalHelpers(typeSpec, model);

        // Generate CommandInfoExposer implementation
        buildCommandInfoExposer(typeSpec, model);

        // Generate static factory method for least-reflection instantiation
        typeSpec.addMethod(MethodSpec.methodBuilder("factory")
                .addJavadoc("Instantiates this wrapper. Called by the platform manager via {@link $T}.\n\n"
                        + "@param instance the annotated command instance\n"
                        + "@param manager the command manager\n"
                        + "@return the instantiated wrapper\n",
                        ClassName.get("io.github.projectunified.craftcommand", "CommandFactory"))
                .addModifiers(Modifier.PUBLIC, Modifier.STATIC)
                .returns(getCommandInterfaceType())
                .addParameter(Object.class, "instance")
                .addParameter(getManagerType(), "manager")
                .addStatement("return new $T(($T) instance, manager)",
                        ClassName.get(model.getPackageName(), wrapperClassName), model.getClassName())
                .build());

        JavaFile javaFile = JavaFile.builder(model.getPackageName(), typeSpec.build())
                .skipJavaLangImports(true)
                .build();
        javaFile.writeTo(processingEnv.getFiler());
    }

    // ── Subcommand Class Executors Generation ──

    protected void generateFieldsAndConstructorStatements(CommandModel model, TypeSpec.Builder typeSpec, MethodSpec.Builder constructor, String parentFieldName) {
        for (CommandModel child : model.getNestedSubcommands()) {
            String fieldName = getSubcommandFieldName(child);
            typeSpec.addField(child.getClassName(), fieldName, Modifier.PRIVATE, Modifier.FINAL);
            boolean isStatic = child.getElement().getModifiers().contains(Modifier.STATIC);
            if (isStatic) {
                constructor.addStatement("this.$L = new $T()", fieldName, child.getClassName());
            } else {
                constructor.addStatement("this.$L = $L.new $L()", fieldName, parentFieldName, child.getClassName().simpleName());
            }
            generateFieldsAndConstructorStatements(child, typeSpec, constructor, fieldName);
        }
    }

    protected CodeBlock buildAliasesExpression(CommandModel model) {
        CodeBlock.Builder aliasesBlock = CodeBlock.builder().add("$T.asList(", java.util.Arrays.class);
        for (int i = 0; i < model.getAliases().size(); i++) {
            aliasesBlock.add("$S", model.getAliases().get(i));
            if (i < model.getAliases().size() - 1) {
                aliasesBlock.add(", ");
            }
        }
        aliasesBlock.add(")");
        return aliasesBlock.build();
    }

    protected void generateExecuteMethodBody(MethodSpec.Builder executeSpec, CommandModel model, String returnStatement) {
        executeSpec.beginControlFlow("try");
        buildExecutionRouting(executeSpec, model, "args", "instance", model, returnStatement);
        executeSpec.nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("manager.getErrorHandler().handle(sender, e)")
                .endControlFlow();
        executeSpec.addStatement("$L", returnStatement);
    }

    // ── Command Execution Routing ──

    protected String getSubcommandFieldName(CommandModel child) {
        return Naming.subcommandField(child.getClassName());
    }

    // ── Local Resolver Helper Utilities ──

    public String getInstanceVarExpression(CommandModel classModel, CommandModel rootModel) {
        if (classModel == rootModel) {
            return "instance";
        }
        return "this." + getSubcommandFieldName(classModel);
    }

    protected String getParameterSuggestionMethodName(CommandModel classModel, MethodModel method, int index) {
        String classPath = Naming.classPath(classModel.getClassName());
        String methodOrDefault = method.isDefault() ? "default" : method.getSubcommandName();
        return Naming.suggestMethod(classPath, methodOrDefault, index);
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
            buildExecutionRouting(methodSpec, child, "args", childInstanceVar, rootModel, "return");

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

    protected void buildExecutionRouting(MethodSpec.Builder methodSpec, CommandModel model, String argsVar, String instanceVar, CommandModel rootModel, String returnStatement) {
        if (!model.getSubcommands().isEmpty() || !model.getNestedSubcommands().isEmpty()) {
            methodSpec.beginControlFlow("if ($L.length >= 1)", argsVar);
            methodSpec.addStatement("String sub = $L[0].toLowerCase()", argsVar);
            methodSpec.addStatement("String[] subArgs = $T.copyOfRange($L, 1, $L.length)", Arrays.class, argsVar, argsVar);

            // 1. Route to nested subcommand classes
            for (CommandModel child : model.getNestedSubcommands()) {
                List<String> names = new ArrayList<>();
                names.add(child.getCommandName().toLowerCase());
                for (String alias : child.getAliases()) {
                    names.add(alias.toLowerCase());
                }

                CodeBlock.Builder cond = CodeBlock.builder().add("if (");
                for (int i = 0; i < names.size(); i++) {
                    cond.add("sub.equals($S)", names.get(i));
                    if (i < names.size() - 1) {
                        cond.add(" || ");
                    }
                }
                cond.add(")");

                methodSpec.beginControlFlow(cond.build().toString());
                onBeforeExecute(methodSpec, child.getElement(), returnStatement);
                String helperMethodName = Naming.executeHelper(child.getClassName());
                methodSpec.addStatement("$L(sender, subArgs)", helperMethodName);
                methodSpec.addStatement("$L", returnStatement);
                methodSpec.endControlFlow();
            }

            // 2. Route to subcommand methods
            for (MethodModel sub : model.getSubcommands()) {
                List<String> names = new ArrayList<>();
                names.add(sub.getSubcommandName().toLowerCase());
                for (String alias : sub.getAliases()) {
                    names.add(alias.toLowerCase());
                }

                CodeBlock.Builder cond = CodeBlock.builder().add("if (");
                for (int i = 0; i < names.size(); i++) {
                    cond.add("sub.equals($S)", names.get(i));
                    if (i < names.size() - 1) {
                        cond.add(" || ");
                    }
                }
                cond.add(")");

                methodSpec.beginControlFlow(cond.build().toString());
                onBeforeExecute(methodSpec, sub.getElement(), returnStatement);
                buildMethodExecution(methodSpec, model, sub, "subArgs", instanceVar, rootModel);
                methodSpec.addStatement("$L", returnStatement);
                methodSpec.endControlFlow();
            }
            methodSpec.endControlFlow();
        }

        // 3. Route to Default method
        if (model.getDefaultMethod() != null) {
            onBeforeExecute(methodSpec, model.getDefaultMethod().getElement(), "return");
            buildMethodExecution(methodSpec, model, model.getDefaultMethod(), argsVar, instanceVar, rootModel);
        } else {
            generateUnknownSubcommandMessage(methodSpec, model);
        }
    }

    protected int getLocalResolverMinWidth(ExecutableElement resolverMethod) {
        int minWidth = 0;
        List<? extends VariableElement> params = resolverMethod.getParameters();
        int startIndex = firstParamIsSender(resolverMethod) ? 1 : 0;
        for (int i = startIndex; i < params.size(); i++) {
            if (params.get(i).getAnnotation(Optional.class) == null) {
                minWidth++;
            }
        }
        return minWidth;
    }

    // ── Parameter Resolution and Method Invocation ──

    protected int getLocalResolverMaxWidth(ExecutableElement resolverMethod) {
        List<? extends VariableElement> params = resolverMethod.getParameters();
        int startIndex = firstParamIsSender(resolverMethod) ? 1 : 0;
        return params.size() - startIndex;
    }

    // ── Tab Completion Suggestion Routing ──

    public boolean firstParamIsSender(ExecutableElement method) {
        if (method.getParameters().isEmpty()) {
            return false;
        }
        TypeName type = TypeName.get(method.getParameters().get(0).asType());
        return isSenderType(type);
    }

    private void generateResolveSingleArgument(MethodSpec.Builder methodSpec, TypeMirror type, String varName, String argStrVar, String senderVar, String argsVar) {
        TypeName typeName = TypeName.get(type);
        if (typeName.toString().equals("java.lang.String")) {
            methodSpec.addStatement("$L = $L", varName, argStrVar);
        } else if (isBuiltInType(typeName)) {
            String defVal = typeName.isPrimitive() ? getDefaultPrimitiveValue(type) : "null";
            String name = typeName.toString();
            if (name.equals("int") || name.equals("java.lang.Integer")) {
                methodSpec.addStatement("$L = $L == null ? $L : $T.parseInt($L)", varName, argStrVar, defVal, Integer.class, argStrVar);
            } else if (name.equals("double") || name.equals("java.lang.Double")) {
                methodSpec.addStatement("$L = $L == null ? $L : $T.parseDouble($L)", varName, argStrVar, defVal, Double.class, argStrVar);
            } else if (name.equals("float") || name.equals("java.lang.Float")) {
                methodSpec.addStatement("$L = $L == null ? $L : $T.parseFloat($L)", varName, argStrVar, defVal, Float.class, argStrVar);
            } else if (name.equals("long") || name.equals("java.lang.Long")) {
                methodSpec.addStatement("$L = $L == null ? $L : $T.parseLong($L)", varName, argStrVar, defVal, Long.class, argStrVar);
            } else if (name.equals("short") || name.equals("java.lang.Short")) {
                methodSpec.addStatement("$L = $L == null ? $L : $T.parseShort($L)", varName, argStrVar, defVal, Short.class, argStrVar);
            } else if (name.equals("byte") || name.equals("java.lang.Byte")) {
                methodSpec.addStatement("$L = $L == null ? $L : $T.parseByte($L)", varName, argStrVar, defVal, Byte.class, argStrVar);
            } else {
                methodSpec.beginControlFlow("if ($L == null)", argStrVar)
                        .addStatement("$L = $L", varName, defVal)
                        .nextControlFlow("else");
                resolveParameter(methodSpec, typeName, varName, argStrVar);
                methodSpec.endControlFlow();
            }
        } else {
            if (typeName.isPrimitive()) {
                String defVal = getDefaultPrimitiveValue(type);
                methodSpec.addStatement("$L = $L == null ? $L : ($T) manager.getResolver($T.class).resolve($L, new String[]{$L}, $L)",
                        varName, argStrVar, defVal, typeName.box(), typeName.box(), senderVar, argStrVar, argStrVar);
            } else {
                methodSpec.addStatement("$L = $L == null ? null : ($T) manager.getResolver($T.class).resolve($L, new String[]{$L}, $L)",
                        varName, argStrVar, typeName, typeName, senderVar, argStrVar, argStrVar);
            }
        }
    }

    // ── Parameter Suggestion Helpers Generation ──

    private void generateAssignDefaultValue(MethodSpec.Builder methodSpec, TypeMirror type, String varName, String defaultValue) {
        TypeName typeName = TypeName.get(type);
        if (isBuiltInType(typeName)) {
            if (defaultValue == null) {
                String defVal = typeName.isPrimitive() ? getDefaultPrimitiveValue(type) : "null";
                methodSpec.addStatement("$L = $L", varName, defVal);
            } else {
                String name = typeName.toString();
                if (name.equals("char") || name.equals("java.lang.Character")) {
                    char c = defaultValue.length() == 1 ? defaultValue.charAt(0) : ' ';
                    methodSpec.addStatement("$L = '$L'", varName, c);
                } else if (name.equals("short") || name.equals("java.lang.Short")) {
                    try {
                        methodSpec.addStatement("$L = (short) $L", varName, Short.parseShort(defaultValue));
                    } catch (NumberFormatException e) {
                        methodSpec.addStatement("$L = $T.parseShort($S)", varName, Short.class, defaultValue);
                    }
                } else if (name.equals("byte") || name.equals("java.lang.Byte")) {
                    try {
                        methodSpec.addStatement("$L = (byte) $L", varName, Byte.parseByte(defaultValue));
                    } catch (NumberFormatException e) {
                        methodSpec.addStatement("$L = $T.parseByte($S)", varName, Byte.class, defaultValue);
                    }
                } else if (name.equals("java.lang.String") || name.equals("int") || name.equals("java.lang.Integer")
                        || name.equals("long") || name.equals("java.lang.Long")
                        || name.equals("double") || name.equals("java.lang.Double")
                        || name.equals("float") || name.equals("java.lang.Float")
                        || name.equals("boolean") || name.equals("java.lang.Boolean")) {
                    methodSpec.addStatement("$L = $L", varName, getAssignmentValueForType(typeName, defaultValue));
                } else {
                    methodSpec.addStatement("$T tempDefault = $S", String.class, defaultValue);
                    resolveParameter(methodSpec, typeName, varName, "tempDefault");
                }
            }
        } else {
            if (typeName.isPrimitive()) {
                methodSpec.addStatement("$L = $L", varName, getDefaultPrimitiveValue(type));
            } else {
                if (defaultValue == null) {
                    methodSpec.addStatement("$L = null", varName);
                } else {
                    methodSpec.addStatement("$L = ($T) manager.getResolver($T.class).resolve(senderCast, new String[]{$S}, $S)",
                            varName, typeName, typeName, defaultValue, defaultValue);
                }
            }
        }
    }

    public CodeBlock getAssignmentValueForType(TypeName typeName, String defaultValue) {
        CodeBlock lit = typeSupport.literal(typeName, defaultValue);
        return lit != null ? lit : CodeBlock.of("null");
    }

    protected void buildMethodExecution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, String argsVar, String instanceVar, CommandModel rootModel) {
        boolean hasDynamic = false;
        for (ParameterModel pm : method.getParameters()) {
            TypeName pmTypeName = TypeName.get(pm.getType());
            if (findLocalResolver(classModel, pm, rootModel) == null && !isBuiltInType(pmTypeName)) {
                hasDynamic = true;
                break;
            }
        }
        String argIdxVar = hasDynamic ? "argIdxHolder[0]" : "argIdx";

        ArrayExecutionSource source = new ArrayExecutionSource(this, argsVar, hasDynamic, argIdxVar);
        buildMethodExecution(methodSpec, classModel, method, instanceVar, rootModel, source);
    }

    protected void buildMethodExecution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, String instanceVar, CommandModel rootModel, ExecutionSource source) {

        // 1. Resolve and Cast the Sender Parameter (index 0)
        ParameterModel senderParam = method.getSenderParameter();
        TypeName senderParamTypeName = TypeName.get(senderParam.getType());
        String senderVarName = "senderCast";

        source.generateSenderResolution(methodSpec, classModel, method, rootModel, senderVarName, senderParam, senderParamTypeName);
        runSPIAnnotationHandlers(methodSpec, method, instanceVar, senderVarName, senderParam);

        // 2. Setup execution context / static checks
        source.generateExecutionSetup(methodSpec, classModel, method, rootModel);

        // 3. Resolve and Validate Argument Parameters
        List<String> paramNames = new ArrayList<>();
        paramNames.add(senderVarName);

        for (int i = 0; i < method.getParameters().size(); i++) {
            ParameterModel p = method.getParameters().get(i);
            String varName = "param_" + i;
            paramNames.add(varName);

            // Let the platform resolve the parameter
            source.generateParameterResolution(methodSpec, classModel, method, rootModel, p, varName, senderVarName, i);

            // Run SPI parameter annotation handlers on normal parameters
            for (ParameterAnnotationHandler<?> handler : parameterHandlers) {
                Annotation ann = p.getElement().getAnnotation(handler.annotationType());
                if (ann != null) {
                    invokeParameterHandler(handler, ann, p, varName, instanceVar, senderVarName, methodSpec);
                }
            }
        }

        // 4. Call Target Command Method Directly
        StringBuilder call = new StringBuilder(instanceVar).append(".").append(method.getMethodName()).append("(");
        for (int i = 0; i < paramNames.size(); i++) {
            call.append(paramNames.get(i));
            if (i < paramNames.size() - 1) {
                call.append(", ");
            }
        }
        call.append(")");
        methodSpec.addStatement(call.toString());
    }

    protected void buildSenderResolution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, String senderVarName, ParameterModel senderParam, TypeName senderParamTypeName) {
        ExecutableElement senderResolver = findLocalResolver(classModel, senderParam, rootModel);
        if (senderResolver != null) {
            TypeElement resolverClass = (TypeElement) senderResolver.getEnclosingElement();
            CommandModel resolverModel = findModelForClass(rootModel, resolverClass);
            String resolverInstanceExpr = getInstanceVarExpression(resolverModel, rootModel);
            String resolveExpr = generateLocalResolverInvocation(senderResolver, resolverInstanceExpr, "sender", "new String[0]", "sender");
            methodSpec.addStatement("$T $L = ($T) $L", senderParamTypeName, senderVarName, senderParamTypeName, resolveExpr);
        } else {
            if (!isSenderBaseType(senderParamTypeName)) {
                String castMethodName = "as" + getSimpleName(senderParamTypeName);
                methodSpec.addStatement("$T $L = $L(sender)", senderParamTypeName, senderVarName, castMethodName);
            } else {
                methodSpec.addStatement("$T $L = sender", getSenderTypeName(), senderVarName);
            }
        }
    }

    private void runSPIAnnotationHandlers(MethodSpec.Builder methodSpec, MethodModel method, String instanceVar, String senderVarName, ParameterModel senderParam) {
        // Run SPI method annotation handlers
        for (MethodAnnotationHandler<?> handler : methodHandlers) {
            Annotation ann = method.getElement().getAnnotation(handler.annotationType());
            if (ann != null) {
                invokeMethodHandler(handler, ann, method, instanceVar, senderVarName, methodSpec);
            }
        }

        // Run SPI parameter annotation handlers on the sender parameter
        for (ParameterAnnotationHandler<?> handler : parameterHandlers) {
            Annotation ann = senderParam.getElement().getAnnotation(handler.annotationType());
            if (ann != null) {
                invokeParameterHandler(handler, ann, senderParam, senderVarName, instanceVar, "sender", methodSpec);
            }
        }
    }

    private void buildParameterParsing(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, String argsVar, String instanceVar, CommandModel rootModel, List<String> paramNames, String senderVarName, boolean hasDynamic, String argIdxVar) {
        for (int i = 0; i < method.getParameters().size(); i++) {
            ParameterModel p = method.getParameters().get(i);
            TypeName pTypeName = TypeName.get(p.getType());
            String varName = "param_" + i;
            paramNames.add(varName);

            ExecutableElement localResolver = findLocalResolver(classModel, p, rootModel);

            if (localResolver != null) {
                buildLocalResolverParameter(methodSpec, classModel, p, pTypeName, varName, localResolver, rootModel, senderVarName, argsVar, argIdxVar, hasDynamic, i);
            } else {
                if (isBuiltInType(pTypeName)) {
                    buildBuiltInParameter(methodSpec, p, pTypeName, varName, argsVar, argIdxVar, senderVarName, hasDynamic, i);
                } else {
                    String resolverMethodName = getResolverMethodName(pTypeName.isPrimitive() ? pTypeName.box() : pTypeName);
                    methodSpec.addStatement("$T $L", pTypeName, varName);
                    String defValLiteral = p.getDefaultValue() == null ? "null" : CodeBlock.of("$S", p.getDefaultValue()).toString();
                    methodSpec.addStatement("$L = $L(senderCast, $L, $S, argIdxHolder, $L, $L)",
                            varName, resolverMethodName,
                            argsVar, p.getName(), p.isOptional(), defValLiteral);
                }
            }

            // Run SPI parameter annotation handlers on normal parameters
            for (ParameterAnnotationHandler<?> handler : parameterHandlers) {
                Annotation ann = p.getElement().getAnnotation(handler.annotationType());
                if (ann != null) {
                    invokeParameterHandler(handler, ann, p, varName, instanceVar, senderVarName, methodSpec);
                }
            }
        }
    }

    protected void buildLocalResolverParameter(MethodSpec.Builder methodSpec, CommandModel classModel, ParameterModel p, TypeName pTypeName, String varName, ExecutableElement localResolver, CommandModel rootModel, String senderVarName, String argsVar, String argIdxVar, boolean hasDynamic, int i) {
        // Statically compile-time resolved parameter width
        int minWidth_i = getLocalResolverMinWidth(localResolver);
        int maxWidth_i = getLocalResolverMaxWidth(localResolver);

        methodSpec.beginControlFlow("if ($L + $L > $L.length)", argIdxVar, minWidth_i, argsVar)
                .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                        CommandException.class,
                        "missing-argument",
                        "Missing arguments for parameter: %s",
                        p.getName())
                .endControlFlow();

        methodSpec.addStatement("int actualWidth_$L = $T.min($L, $L.length - $L)", i, Math.class, maxWidth_i, argsVar, argIdxVar);

        // Declare rp variables and resolve them
        List<? extends VariableElement> resolverParams = localResolver.getParameters();
        int resolverStartIndex = firstParamIsSender(localResolver) ? 1 : 0;
        List<String> resolverArgVarNames = new ArrayList<>();
        int resolverArgIdx = 0;

        for (int j = resolverStartIndex; j < resolverParams.size(); j++) {
            VariableElement rp = resolverParams.get(j);
            TypeName rpTypeName = TypeName.get(rp.asType());
            String rpVarName = varName + "_rp_" + resolverArgIdx;
            resolverArgVarNames.add(rpVarName);

            Optional optionalAnn = rp.getAnnotation(Optional.class);
            boolean isOptional = optionalAnn != null;
            String defaultValue = isOptional ? optionalAnn.value() : null;

            methodSpec.addStatement("$T $L", rpTypeName, rpVarName);
            String rpArgStrName = "rpArgStr_" + i + "_" + resolverArgIdx;
            if (isOptional) {
                methodSpec.beginControlFlow("if (actualWidth_$L > $L)", i, resolverArgIdx)
                        .addStatement("String $L = $L[$L + $L]", rpArgStrName, argsVar, argIdxVar, resolverArgIdx);
                generateResolveSingleArgument(methodSpec, rp.asType(), rpVarName, rpArgStrName, senderVarName, argsVar);
                methodSpec.nextControlFlow("else");
                generateAssignDefaultValue(methodSpec, rp.asType(), rpVarName, defaultValue);
                methodSpec.endControlFlow();
            } else {
                methodSpec.addStatement("String $L = $L[$L + $L]", rpArgStrName, argsVar, argIdxVar, resolverArgIdx);
                generateResolveSingleArgument(methodSpec, rp.asType(), rpVarName, rpArgStrName, senderVarName, argsVar);
            }
            resolverArgIdx++;
        }

        // Invoke Local Resolver
        TypeElement resolverClass = (TypeElement) localResolver.getEnclosingElement();
        CommandModel resolverModel = findModelForClass(rootModel, resolverClass);
        String resolverInstanceExpr = getInstanceVarExpression(resolverModel, rootModel);

        StringBuilder resolveCall = new StringBuilder(resolverInstanceExpr).append(".").append(localResolver.getSimpleName()).append("(");
        if (firstParamIsSender(localResolver)) {
            resolveCall.append(senderVarName);
            if (!resolverArgVarNames.isEmpty()) {
                resolveCall.append(", ");
            }
        }
        for (int j = 0; j < resolverArgVarNames.size(); j++) {
            resolveCall.append(resolverArgVarNames.get(j));
            if (j < resolverArgVarNames.size() - 1) {
                resolveCall.append(", ");
            }
        }
        resolveCall.append(")");

        methodSpec.addStatement("$T $L = ($T) $L", pTypeName, varName, pTypeName, resolveCall.toString())
                .addStatement("$L += actualWidth_$L", argIdxVar, i);
    }

    protected void buildBuiltInParameter(MethodSpec.Builder methodSpec, ParameterModel p, TypeName pTypeName, String varName, String argsVar, String argIdxVar, String senderVarName, boolean hasDynamic, int i) {
        if (!p.isOptional()) {
            int width = getBuiltInWidth(pTypeName);
            if (hasDynamic) {
                methodSpec.beginControlFlow("if ($L + $L > $L.length)", argIdxVar, width, argsVar)
                        .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                                CommandException.class,
                                "missing-argument",
                                "Missing arguments for parameter: %s",
                                p.getName())
                        .endControlFlow();
            }
            if (width == 1) {
                if (pTypeName.toString().equals("java.lang.String")) {
                    if (p.isGreedy()) {
                        methodSpec.addStatement("$T $L = String.join($S, $T.copyOfRange($L, $L, $L.length))", pTypeName, varName, " ", Arrays.class, argsVar, argIdxVar, argsVar);
                    } else {
                        methodSpec.addStatement("$T $L = $L[$L++]", pTypeName, varName, argsVar, argIdxVar);
                    }
                } else {
                    methodSpec.addStatement("$T $L", pTypeName, varName);
                    methodSpec.addStatement("String argStr_$L = $L[$L++]", i, argsVar, argIdxVar);
                    String defVal = pTypeName.isPrimitive() ? getDefaultPrimitiveValue(p.getType()) : "null";
                    methodSpec.beginControlFlow("if (argStr_$L == null)", i)
                            .addStatement("$L = $L", varName, defVal)
                            .nextControlFlow("else");
                    resolveParameter(methodSpec, pTypeName, varName, "argStr_" + i);
                    methodSpec.endControlFlow();
                }
            } else {
                methodSpec.addStatement("$T $L", pTypeName, varName);
                resolveMultiParameter(methodSpec, pTypeName, varName, argsVar, argIdxVar, senderVarName, i);
                methodSpec.addStatement("$L += $L", argIdxVar, width);
            }
        } else {
            int width = getBuiltInWidth(pTypeName);
            if (width == 1) {
                if (pTypeName.toString().equals("java.lang.String")) {
                    String defVal = p.getDefaultValue() == null ? "null" : CodeBlock.of("$S", p.getDefaultValue()).toString();
                    methodSpec.addStatement("$T $L = $L >= $L.length ? $L : $L[$L++]", pTypeName, varName, argIdxVar, argsVar, defVal, argsVar, argIdxVar);
                } else {
                    methodSpec.addStatement("$T $L", pTypeName, varName);
                    methodSpec.beginControlFlow("if ($L >= $L.length)", argIdxVar, argsVar);
                    generateAssignDefaultValue(methodSpec, p.getType(), varName, p.getDefaultValue());
                    methodSpec.nextControlFlow("else");
                    methodSpec.addStatement("String argStr_$L = $L[$L++]", i, argsVar, argIdxVar);
                    String defVal = pTypeName.isPrimitive() ? getDefaultPrimitiveValue(p.getType()) : "null";
                    methodSpec.beginControlFlow("if (argStr_$L == null)", i)
                            .addStatement("$L = $L", varName, defVal)
                            .nextControlFlow("else");
                    resolveParameter(methodSpec, pTypeName, varName, "argStr_" + i);
                    methodSpec.endControlFlow();
                    methodSpec.endControlFlow();
                }
            } else {
                methodSpec.beginControlFlow("if ($L + $L > $L.length)", argIdxVar, width, argsVar);
                generateAssignDefaultValue(methodSpec, p.getType(), varName, p.getDefaultValue());
                methodSpec.nextControlFlow("else");
                resolveMultiParameter(methodSpec, pTypeName, varName, argsVar, argIdxVar, senderVarName, i);
                methodSpec.addStatement("$L += $L", argIdxVar, width);
                methodSpec.endControlFlow();
            }
        }
    }

    // ── Local Resolver Resolution Logic ──

    protected void buildSuggestionRouting(MethodSpec.Builder methodSpec, CommandModel model, String argsVar, String instanceVar, CommandModel rootModel) {
        boolean hasChildren = !model.getSubcommands().isEmpty() || !model.getNestedSubcommands().isEmpty();

        if (hasChildren) {
            methodSpec.beginControlFlow("if ($L.length == 1)", argsVar);
            methodSpec.addStatement("String current = $L[0]", argsVar);
            methodSpec.addStatement("$T<$T> suggestions = new $T<>()", List.class, String.class, ArrayList.class);

            // Nested subcommand classes
            for (CommandModel child : model.getNestedSubcommands()) {
                methodSpec.addStatement("suggestions.add($S)", child.getCommandName());
                for (String alias : child.getAliases()) {
                    methodSpec.addStatement("suggestions.add($S)", alias);
                }
            }

            // Subcommand methods
            for (MethodModel sub : model.getSubcommands()) {
                methodSpec.addStatement("suggestions.add($S)", sub.getSubcommandName());
                for (String alias : sub.getAliases()) {
                    methodSpec.addStatement("suggestions.add($S)", alias);
                }
            }

            // First parameter of default method
            if (model.getDefaultMethod() != null && !model.getDefaultMethod().getParameters().isEmpty()) {
                ParameterModel p0 = model.getDefaultMethod().getParameters().get(0);
                if (!isParamSuggestionEmpty(p0)) {
                    String helperName = getParameterSuggestionMethodName(model, model.getDefaultMethod(), 0);
                    methodSpec.addStatement("suggestions.addAll($L(sender, $L, current))", helperName, argsVar);
                }
            }

            methodSpec.addStatement("return filterSuggestions(suggestions, current)");
            methodSpec.endControlFlow();

            // Routing for args.length > 1
            methodSpec.beginControlFlow("if ($L.length > 1)", argsVar);
            methodSpec.addStatement("String sub = $L[0].toLowerCase()", argsVar);
            methodSpec.addStatement("String[] subArgs = $T.copyOfRange($L, 1, $L.length)", Arrays.class, argsVar, argsVar);

            // Route to nested subcommand classes
            for (CommandModel child : model.getNestedSubcommands()) {
                List<String> names = new ArrayList<>();
                names.add(child.getCommandName().toLowerCase());
                for (String alias : child.getAliases()) {
                    names.add(alias.toLowerCase());
                }

                CodeBlock.Builder cond = CodeBlock.builder().add("if (");
                for (int i = 0; i < names.size(); i++) {
                    cond.add("sub.equals($S)", names.get(i));
                    if (i < names.size() - 1) {
                        cond.add(" || ");
                    }
                }
                cond.add(")");

                methodSpec.beginControlFlow(cond.build().toString());
                String childHelperName = Naming.suggestHelper(child.getClassName());
                methodSpec.addStatement("return $L(sender, subArgs)", childHelperName);
                methodSpec.endControlFlow();
            }

            // Route to subcommand methods
            for (MethodModel sub : model.getSubcommands()) {
                List<String> names = new ArrayList<>();
                names.add(sub.getSubcommandName().toLowerCase());
                for (String alias : sub.getAliases()) {
                    names.add(alias.toLowerCase());
                }

                CodeBlock.Builder cond = CodeBlock.builder().add("if (");
                for (int i = 0; i < names.size(); i++) {
                    cond.add("sub.equals($S)", names.get(i));
                    if (i < names.size() - 1) {
                        cond.add(" || ");
                    }
                }
                cond.add(")");

                methodSpec.beginControlFlow(cond.build().toString());
                buildSubcommandSuggestionRouting(methodSpec, model, sub, "subArgs");
                methodSpec.endControlFlow();
            }
            methodSpec.endControlFlow();
        }

        // Default command tab complete
        if (model.getDefaultMethod() != null) {
            buildSubcommandSuggestionRouting(methodSpec, model, model.getDefaultMethod(), argsVar);
        } else {
            methodSpec.addStatement("return $T.emptyList()", Collections.class);
        }
    }

    private boolean isParamSuggestionEmpty(ParameterModel p) {
        if (p.getSuggestProvider() != null) {
            return false;
        }
        TypeName pTypeName = TypeName.get(p.getType());
        if (pTypeName.toString().equals("boolean") || pTypeName.toString().equals("java.lang.Boolean")) {
            return false;
        }
        if (isPlatformBuiltInType(pTypeName)) {
            return false;
        }
        return isBuiltInType(pTypeName);
    }

    protected void buildSubcommandSuggestionRouting(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, String argsVar) {
        int paramCount = method.getParameters().size();
        if (paramCount == 0) {
            methodSpec.addStatement("return $T.emptyList()", Collections.class);
            return;
        }

        methodSpec.addStatement("int index = $L.length - 1", argsVar);
        methodSpec.addStatement("String current = $L[index]", argsVar);
        methodSpec.addStatement("int tempIdx = 0");

        for (int i = 0; i < paramCount; i++) {
            ParameterModel p = method.getParameters().get(i);
            TypeName pTypeName = TypeName.get(p.getType());
            String helperName = getParameterSuggestionMethodName(classModel, method, i);

            ExecutableElement localResolver = findLocalResolver(classModel, p, classModel);

            String widthExpr;
            if (localResolver != null) {
                int maxWidth = getLocalResolverMaxWidth(localResolver);
                widthExpr = String.valueOf(maxWidth);
            } else {
                if (isBuiltInType(pTypeName)) {
                    widthExpr = "1";
                } else {
                    TypeName boxedType = pTypeName.isPrimitive() ? pTypeName.box() : pTypeName;
                    widthExpr = "manager.getResolver(" + boxedType + ".class).getWidth()";
                }
            }

            methodSpec.beginControlFlow("if (index < tempIdx + $L)", widthExpr);
            if (isParamSuggestionEmpty(p)) {
                methodSpec.addStatement("return $T.emptyList()", Collections.class);
            } else {
                methodSpec.addStatement("return $L(sender, $L, current)", helperName, argsVar);
            }
            methodSpec.endControlFlow();
            methodSpec.addStatement("tempIdx += $L", widthExpr);
        }
        methodSpec.addStatement("return $T.emptyList()", Collections.class);
    }

    // ── SPI Generic Cast Invoke Helpers ──

    protected void buildParameterSuggestions(TypeSpec.Builder typeSpec, CommandModel model, CommandModel rootModel) {
        if (model.getDefaultMethod() != null) {
            for (int i = 0; i < model.getDefaultMethod().getParameters().size(); i++) {
                ParameterModel p = model.getDefaultMethod().getParameters().get(i);
                if (!isParamSuggestionEmpty(p)) {
                    typeSpec.addMethod(buildParameterSuggestionHelper(model, model.getDefaultMethod(), p, i, rootModel));
                }
            }
        }
        for (MethodModel sub : model.getSubcommands()) {
            for (int i = 0; i < sub.getParameters().size(); i++) {
                ParameterModel p = sub.getParameters().get(i);
                if (!isParamSuggestionEmpty(p)) {
                    typeSpec.addMethod(buildParameterSuggestionHelper(model, sub, p, i, rootModel));
                }
            }
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            buildParameterSuggestions(typeSpec, child, rootModel);
        }
    }

    protected MethodSpec buildParameterSuggestionHelper(CommandModel classModel, MethodModel method, ParameterModel p, int index, CommandModel rootModel) {
        String helperName = getParameterSuggestionMethodName(classModel, method, index);
        MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(helperName)
                .addJavadoc("Gets suggestions for parameter {@code $L} at index {@code $L} of method {@code $L}.\n\n"
                        + "@param sender the command sender\n"
                        + "@param args the command arguments\n"
                        + "@param current the current user input string\n"
                        + "@return the list of suggestions\n", p.getName(), index, method.getMethodName())
                .addModifiers(Modifier.PRIVATE)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addParameter(getSenderTypeName(), "sender")
                .addParameter(String[].class, "args")
                .addParameter(String.class, "current");

        TypeName senderTypeName = TypeName.get(method.getSenderType());
        if (!isSenderBaseType(senderTypeName)) {
            methodSpec.beginControlFlow("if (!(" + getSenderExpression("sender") + " instanceof $T))", senderTypeName)
                    .addStatement("return $T.emptyList()", Collections.class)
                    .endControlFlow();
            methodSpec.addStatement("$T senderCast = ($T) " + getSenderExpression("sender"), senderTypeName, senderTypeName);
        } else {
            generateDefaultSenderCastForSuggestion(methodSpec);
        }

        String provider = p.getSuggestProvider();
        String instanceExpr = getInstanceVarExpression(classModel, rootModel);
        if (provider != null) {
            TypeElement typeElement = classModel.getElement();
            ExecutableElement suggestMethod = findSuggestMethod(typeElement, provider);
            if (suggestMethod != null) {
                int argCount = suggestMethod.getParameters().size();
                if (argCount == 0) {
                    methodSpec.addStatement("return filterSuggestions($L.$L(), current)", instanceExpr, provider);
                } else if (argCount == 1) {
                    methodSpec.addStatement("return filterSuggestions($L.$L(senderCast), current)", instanceExpr, provider);
                } else if (argCount == 2) {
                    methodSpec.addStatement("return filterSuggestions($L.$L(senderCast, current), current)", instanceExpr, provider);
                } else if (argCount == 3) {
                    methodSpec.addStatement("return filterSuggestions($L.$L(senderCast, args, current), current)", instanceExpr, provider);
                } else {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Invalid signature for suggest method: " + provider + ". Must accept 0-3 parameters matching (sender, args, current).", suggestMethod);
                    methodSpec.addStatement("return $T.emptyList()", Collections.class);
                }
            } else if (isField(typeElement, provider)) {
                methodSpec.addStatement("return filterSuggestions($L.$L, current)", instanceExpr, provider);
            } else {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Could not find a field or method named '" + provider + "' for parameter suggestions in class " + classModel.getClassName().simpleName());
                methodSpec.addStatement("return $T.emptyList()", Collections.class);
            }
        } else {
            TypeName pTypeName = TypeName.get(p.getType());
            if (pTypeName.toString().equals("boolean") || pTypeName.toString().equals("java.lang.Boolean")) {
                methodSpec.addStatement("return suggestBoolean(current)");
            } else if (isPlatformBuiltInType(pTypeName)) {
                int tempIdx = 0;
                for (int j = 0; j < index; j++) {
                    ParameterModel prev = method.getParameters().get(j);
                    tempIdx += getBuiltInWidth(TypeName.get(prev.getType()));
                }
                generatePlatformParamSuggestions(methodSpec, pTypeName, "senderCast", "args", "current", tempIdx);
            } else if (isBuiltInType(pTypeName)) {
                methodSpec.addStatement("return $T.emptyList()", Collections.class);
            } else {
                methodSpec.addStatement("return manager.getResolver($T.class).suggest(($T) sender, args, current)", pTypeName.isPrimitive() ? pTypeName.box() : pTypeName, getSenderTypeName());
            }
        }

        return methodSpec.build();
    }

    // ── Basic Utility Helpers ──

    public ExecutableElement findLocalResolver(CommandModel classModel, ParameterModel p, CommandModel rootModel) {
        return resolverLookup.findLocalResolver(classModel, p);
    }

    protected String generateLocalResolverInvocation(ExecutableElement resolverMethod, String instanceExpr, String senderVar, String argsVar, String currentVar) {
        int paramCount = resolverMethod.getParameters().size();
        String methodName = resolverMethod.getSimpleName().toString();
        if (paramCount == 0) {
            return String.format("%s.%s()", instanceExpr, methodName);
        } else if (paramCount == 1) {
            return String.format("%s.%s(%s)", instanceExpr, methodName, currentVar);
        } else if (paramCount == 2) {
            return String.format("%s.%s(%s, %s)", instanceExpr, methodName, senderVar, currentVar);
        } else if (paramCount == 3) {
            return String.format("%s.%s(%s, %s, %s)", instanceExpr, methodName, senderVar, argsVar, currentVar);
        }
        throw new IllegalArgumentException("Invalid parameter count for resolver method " + methodName);
    }

    @SuppressWarnings("unchecked")
    private <A extends Annotation> void invokeParameterHandler(
            ParameterAnnotationHandler<A> handler,
            Annotation annotation,
            ParameterModel parameter,
            String varName,
            String instanceExpr,
            String senderVar,
            MethodSpec.Builder methodSpec) {
        handler.handle((A) annotation, parameter, varName, instanceExpr, senderVar, methodSpec);
    }

    @SuppressWarnings("unchecked")
    private <A extends Annotation> void invokeMethodHandler(
            MethodAnnotationHandler<A> handler,
            Annotation annotation,
            MethodModel method,
            String instanceExpr,
            String senderVar,
            MethodSpec.Builder methodSpec) {
        handler.handle((A) annotation, method, instanceExpr, senderVar, methodSpec);
    }

    protected ExecutableElement findSuggestMethod(TypeElement typeElement, String name) {
        return resolverLookup.findMethod(typeElement, name);
    }

    protected boolean isField(TypeElement typeElement, String name) {
        return resolverLookup.isField(typeElement, name);
    }

    public CommandModel findModelForClass(CommandModel current, TypeElement targetClass) {
        return resolverLookup.findModelForClass(current, targetClass);
    }

    public boolean isBuiltInType(TypeName typeName) {
        return typeSupport.isBuiltIn(typeName) || isPlatformBuiltInType(typeName);
    }

    private int getBuiltInWidth(TypeName typeName) {
        if (typeSupport.isBuiltIn(typeName)) {
            return typeSupport.getWidth(typeName);
        }
        if (isPlatformBuiltInType(typeName)) {
            return getPlatformBuiltInWidth(typeName);
        }
        return 1;
    }

    private void resolveParameter(MethodSpec.Builder methodSpec, TypeName typeName, String varName, String argStrVar) {
        if (typeSupport.isBuiltIn(typeName)) {
            typeSupport.emitParse(methodSpec, typeName, varName, argStrVar);
        } else if (isPlatformBuiltInType(typeName)) {
            resolvePlatformParameter(methodSpec, typeName, varName, argStrVar);
        }
    }

    private void resolveMultiParameter(MethodSpec.Builder methodSpec, TypeName typeName, String varName, String argsVar, String argIdxVar, String senderVar, int i) {
        if (isPlatformBuiltInType(typeName)) {
            resolvePlatformMultiParameter(methodSpec, typeName, varName, argsVar, argIdxVar, senderVar, i);
        }
    }

    protected boolean isPlatformBuiltInType(TypeName typeName) {
        return false;
    }

    protected int getPlatformBuiltInWidth(TypeName typeName) {
        return 1;
    }

    protected void resolvePlatformParameter(MethodSpec.Builder methodSpec, TypeName typeName, String varName, String argStrVar) {
        // No-op by default
    }

    protected void resolvePlatformMultiParameter(MethodSpec.Builder methodSpec, TypeName typeName, String varName, String argsVar, String argIdxVar, String senderVar, int i) {
        // No-op by default
    }

    protected void generatePlatformParamSuggestions(MethodSpec.Builder methodSpec, TypeName typeName, String senderCastVar, String argsVar, String currentVar, int tempIdx) {
        // No-op by default
    }

    /**
     * Constructs a unique, valid Java helper method name for resolving a given type.
     * Combines nested simple class names to ensure readability (e.g. TestCommand_TestEnum).
     *
     * @param typeName the parameter type name
     * @return the resolver method name starting with "resolve_"
     */
    public String getResolverMethodName(TypeName typeName) {
        return Naming.resolverMethod(typeName);
    }

    /**
     * Recursively retrieves all unique custom parameter types requiring dynamic/global resolvers
     * across the entire command hierarchy (main class, nested subcommand classes, and methods).
     *
     * @param model the command model
     * @return a set of unique TypeNames for the dynamic resolvers
     */
    protected Set<TypeName> getDynamicResolverTypes(CommandModel model) {
        Set<TypeName> types = new LinkedHashSet<>();
        collectDynamicResolverTypes(model, types);
        return types;
    }

    /**
     * Recursively traverses subcommands and nested command trees to collect types requiring global resolvers.
     */
    private void collectDynamicResolverTypes(CommandModel model, Set<TypeName> types) {
        if (model.getDefaultMethod() != null) {
            collectDynamicResolverTypes(model, model.getDefaultMethod(), types);
        }
        for (MethodModel sub : model.getSubcommands()) {
            collectDynamicResolverTypes(model, sub, types);
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            collectDynamicResolverTypes(child, types);
        }
    }

    /**
     * Evaluates parameters of a specific command method and adds any non-built-in types that
     * do not have a local @Resolve method.
     */
    private void collectDynamicResolverTypes(CommandModel classModel, MethodModel method, Set<TypeName> types) {
        for (ParameterModel p : method.getParameters()) {
            TypeName pTypeName = TypeName.get(p.getType());
            if (findLocalResolver(classModel, p, classModel) == null && !isBuiltInType(pTypeName)) {
                types.add(pTypeName.isPrimitive() ? pTypeName.box() : pTypeName);
            }
        }
    }

    protected CodeBlock getSenderExpression(String senderVar) {
        return CodeBlock.of("$L", senderVar);
    }

    /**
     * Generates additional platform-independent helper methods (like suggestBoolean and sender casting helpers).
     */
    protected void buildAdditionalHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        if (hasBooleanParameter(model)) {
            typeSpec.addMethod(MethodSpec.methodBuilder("suggestBoolean")
                    .addJavadoc("Suggests boolean values matching the current input (case-insensitive).\n\n"
                            + "@param current the current user input\n"
                            + "@return a list of matching boolean suggestions\n")
                    .addModifiers(Modifier.PRIVATE)
                    .returns(ParameterizedTypeName.get(List.class, String.class))
                    .addParameter(String.class, "current")
                    .addStatement("$T list = new $T()", ParameterizedTypeName.get(List.class, String.class), ArrayList.class)
                    .addStatement("String lower = current.toLowerCase()")
                    .addStatement("if (\"true\".startsWith(lower)) list.add(\"true\")")
                    .addStatement("if (\"false\".startsWith(lower)) list.add(\"false\")")
                    .addStatement("return list")
                    .build());
        }

        for (TypeName type : getSenderTypesToCast(model)) {
            String methodName = "as" + getSimpleName(type);
            typeSpec.addMethod(MethodSpec.methodBuilder(methodName)
                    .addJavadoc("Casts the command sender to {@link $T} after verification.\n\n"
                            + "@param sender the raw command sender\n"
                            + "@return the casted sender\n"
                            + "@throws CommandException if the sender is not of the expected type\n", type)
                    .addModifiers(Modifier.PRIVATE)
                    .returns(type)
                    .addParameter(getSenderTypeName(), "sender")
                    .beginControlFlow("if (!(" + getSenderExpression("sender") + " instanceof $T))", type)
                    .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                            CommandException.class,
                            "invalid-sender",
                            "Only %s can execute this command.",
                            getSimpleName(type))
                    .endControlFlow()
                    .addStatement("return ($T) " + getSenderExpression("sender"), type)
                    .build());
        }
    }

    private Set<TypeName> getSenderTypesToCast(CommandModel model) {
        Set<TypeName> types = new LinkedHashSet<>();
        collectSenderTypesToCast(model, types);
        return types;
    }

    private void collectSenderTypesToCast(CommandModel model, Set<TypeName> types) {
        if (model.getDefaultMethod() != null) {
            collectSenderTypesToCast(model.getDefaultMethod(), types);
        }
        for (MethodModel sub : model.getSubcommands()) {
            collectSenderTypesToCast(sub, types);
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            collectSenderTypesToCast(child, types);
        }
    }

    private void collectSenderTypesToCast(MethodModel method, Set<TypeName> types) {
        ParameterModel senderParam = method.getSenderParameter();
        TypeName typeName = TypeName.get(senderParam.getType());
        if (!isSenderBaseType(typeName)) {
            types.add(typeName);
        }
    }

    private boolean hasBooleanParameter(CommandModel model) {
        if (model.getDefaultMethod() != null && hasBooleanParameter(model.getDefaultMethod())) {
            return true;
        }
        for (MethodModel sub : model.getSubcommands()) {
            if (hasBooleanParameter(sub)) {
                return true;
            }
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            if (hasBooleanParameter(child)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasBooleanParameter(MethodModel method) {
        for (ParameterModel p : method.getParameters()) {
            TypeName typeName = TypeName.get(p.getType());
            if (typeName.toString().equals("boolean") || typeName.toString().equals("java.lang.Boolean")) {
                return true;
            }
        }
        return false;
    }

    private void buildCommandInfoExposer(TypeSpec.Builder typeSpec, CommandModel model) {
        ClassName commandInfoClass = ClassName.get("io.github.projectunified.craftcommand", "CommandInfo");
        MethodSpec.Builder methodSpec = MethodSpec.methodBuilder("getCommandInfo")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(ClassName.get(List.class), commandInfoClass))
                .addStatement("$T<$T> list = new $T<>()", List.class, commandInfoClass, ArrayList.class);

        generateCommandInfoStatements(methodSpec, model, new ArrayList<>(), commandInfoClass);

        methodSpec.addStatement("return list");
        typeSpec.addMethod(methodSpec.build());
    }

    private void generateCommandInfoStatements(MethodSpec.Builder methodSpec, CommandModel model, List<String> parentPath, ClassName commandInfoClass) {
        List<String> currentPath = new ArrayList<>(parentPath);
        currentPath.add(model.getCommandName());

        if (model.getDefaultMethod() != null) {
            String usage = getUsage(model.getDefaultMethod());
            String desc = model.getDescription();

            CodeBlock.Builder pathBuilder = CodeBlock.builder().add("$T.asList(", Arrays.class);
            for (int i = 0; i < currentPath.size(); i++) {
                pathBuilder.add("$S", currentPath.get(i));
                if (i < currentPath.size() - 1) {
                    pathBuilder.add(", ");
                }
            }
            pathBuilder.add(")");

            methodSpec.addStatement("list.add(new $T($L, $S, $S))",
                    commandInfoClass, pathBuilder.build(), usage, desc);
        }

        for (MethodModel sub : model.getSubcommands()) {
            List<String> subPath = new ArrayList<>(currentPath);
            subPath.add(sub.getSubcommandName());

            String usage = getUsage(sub);
            String desc = ""; // Subcommand methods do not have descriptions in Subcommand annotation

            CodeBlock.Builder pathBuilder = CodeBlock.builder().add("$T.asList(", Arrays.class);
            for (int i = 0; i < subPath.size(); i++) {
                pathBuilder.add("$S", subPath.get(i));
                if (i < subPath.size() - 1) {
                    pathBuilder.add(", ");
                }
            }
            pathBuilder.add(")");

            methodSpec.addStatement("list.add(new $T($L, $S, $S))",
                    commandInfoClass, pathBuilder.build(), usage, desc);
        }

        for (CommandModel child : model.getNestedSubcommands()) {
            generateCommandInfoStatements(methodSpec, child, currentPath, commandInfoClass);
        }
    }
}
