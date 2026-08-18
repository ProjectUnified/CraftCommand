package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.annotation.*;
import io.github.projectunified.craftcommand.annotation.Name;
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
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.lang.annotation.Annotation;
import java.util.*;

/**
 * Lightweight, human-readable base annotation processor for CraftCommand.
 * Supports custom validation annotations, parameter resolvers, deep subcommand nesting, and SPI extensions.
 */
public abstract class BaseCommandProcessor extends AbstractProcessor {

    protected final TypeSupport typeSupport = TypeSupport.builtins();
    private final List<ParameterAnnotationHandler<?>> parameterHandlers = new ArrayList<>();
    private final List<MethodAnnotationHandler<?>> methodHandlers = new ArrayList<>();
    private final SenderTypeRegistry senderTypeRegistry = new SenderTypeRegistry();
    protected ResolverLookup resolverLookup;

    protected static String getUsage(MethodModel method, CommandModel classModel) {
        StringBuilder sb = new StringBuilder();
        for (ParameterModel p : method.getParameters()) {
            if (p == method.getSenderParameter()) continue;

            Resolve resolveAnn = p.getElement().getAnnotation(Resolve.class);
            if (resolveAnn != null && !resolveAnn.value().isEmpty() && classModel != null) {
                MethodModel resolverModel = classModel.getResolverMethod(resolveAnn.value());
                if (resolverModel != null) {
                    for (ParameterModel rp : resolverModel.getParameters()) {
                        if (resolverModel.getSenderParameter() != null && rp == resolverModel.getSenderParameter()) {
                            continue;
                        }
                        String paramName = rp.getName();
                        Name nameAnn = rp.getElement().getAnnotation(Name.class);
                        if (nameAnn != null) paramName = nameAnn.value();
                        if (rp.isOptional()) {
                            sb.append("[").append(paramName).append("] ");
                        } else {
                            sb.append("<").append(paramName).append("> ");
                        }
                    }
                    continue;
                }
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

    protected static <A extends Annotation> A findAnnotationUp(Element element, Class<A> annotationType) {
        A ann = element.getAnnotation(annotationType);
        if (ann != null) return ann;
        Element enclosing = element.getEnclosingElement();
        while (enclosing != null) {
            ann = enclosing.getAnnotation(annotationType);
            if (ann != null) return ann;
            enclosing = enclosing.getEnclosingElement();
        }
        return null;
    }

    protected static boolean isI18nKey(String message) {
        return message.startsWith("i18n:");
    }

    protected static String i18nKey(String message) {
        return message.substring(5);
    }

    public TypeSupport typeSupport() {
        return typeSupport;
    }

    // ── Processor Lifecycle ──

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.resolverLookup = new ResolverLookup(processingEnv);
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

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Command.class)) {
            if (element instanceof TypeElement) {
                TypeElement typeElement = (TypeElement) element;
                Element enclosing = typeElement.getEnclosingElement();
                if (enclosing instanceof TypeElement && ((TypeElement) enclosing).getAnnotation(Command.class) != null) {
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

    // ── Platform Customization Hooks ──

    protected void registerTypes(TypeSupport types) {
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

    protected CodeBlock getSenderExpression(String senderVar) {
        return CodeBlock.of("$L", senderVar);
    }

    // ── Class Generation Orchestrator ──

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

    protected void generateHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        generateSubcommandClassExecutors(typeSpec, model, model);
        buildParameterSuggestions(typeSpec, model, model);
        buildSenderCastHelpers(typeSpec, model);
        buildBooleanSuggestionHelper(typeSpec, model);
        generatePlatformHelpers(typeSpec, model);
    }

    // ── Nested Subcommands ──

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

    public String getResolverInstanceExpr(ExecutableElement resolver, CommandModel classModel, CommandModel rootModel) {
        TypeElement resolverClass = (TypeElement) resolver.getEnclosingElement();
        CommandModel resolverModel = findModelForClass(rootModel, resolverClass);
        if (resolverModel != null) {
            return getInstanceVarExpression(resolverModel, rootModel);
        }
        return null;
    }

    // ── Array Execution Routing ──

    public void generateExecuteMethodBody(MethodSpec.Builder executeSpec, CommandModel model, String returnStatement) {
        executeSpec.beginControlFlow("try");
        buildExecutionRouting(executeSpec, model, "args", "instance", model, returnStatement);
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
            methodSpec.beginControlFlow("switch (sub)");

            // 1. Nested subcommand classes
            for (CommandModel child : model.getNestedSubcommands()) {
                for (String name : collectLoweredNames(child)) {
                    methodSpec.addCode("case $S:\n", name);
                }
                methodSpec.addCode("{\n");
                onBeforeExecute(methodSpec, child.getElement(), returnStatement);
                String helperMethodName = Naming.executeHelper(child.getClassName());
                methodSpec.addStatement("$T subArgs = $T.copyOfRange($L, 1, $L.length)", String[].class, Arrays.class, argsVar, argsVar);
                methodSpec.addStatement("$L(sender, subArgs)", helperMethodName);
                methodSpec.addStatement("$L", returnStatement);
                methodSpec.addCode("}\n");
            }

            // 2. Subcommand methods
            for (MethodModel sub : model.getSubcommands()) {
                for (String name : collectLoweredNames(sub)) {
                    methodSpec.addCode("case $S:\n", name);
                }
                methodSpec.addCode("{\n");
                onBeforeExecute(methodSpec, sub.getElement(), returnStatement);
                buildMethodExecution(methodSpec, model, sub, argsVar, 1, instanceVar, rootModel);
                methodSpec.addStatement("$L", returnStatement);
                methodSpec.addCode("}\n");
            }

            methodSpec.endControlFlow(); // switch
            methodSpec.endControlFlow(); // if
        }

        // 3. Default method
        if (model.getDefaultMethod() != null) {
            onBeforeExecute(methodSpec, model.getDefaultMethod().getElement(), returnStatement);
            buildMethodExecution(methodSpec, model, model.getDefaultMethod(), argsVar, 0, instanceVar, rootModel);
        } else {
            generateUnknownSubcommandMessage(methodSpec, model);
        }
    }

    protected void buildMethodExecution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, String argsVar, int initialOffset, String instanceVar, CommandModel rootModel) {
        // 1. Resolve and Cast Sender Parameter
        ParameterModel senderParam = method.getSenderParameter();
        TypeName senderParamTypeName = TypeName.get(senderParam.getType());
        String senderVarName = senderParamTypeName.toString().equals(getSenderTypeName().toString()) ? "sender" : "senderCast";

        resolveSender(methodSpec, classModel, method, rootModel, senderVarName, senderParam, senderParamTypeName);
        runSPIAnnotationHandlers(methodSpec, method, instanceVar, senderVarName, senderParam);

        // 2. Minimum Argument Count Check
        int staticRequiredCount = 0;
        boolean hasDynamic = false;
        for (ParameterModel p : method.getParameters()) {
            TypeName pTypeName = TypeName.get(p.getType());
            if (findLocalResolver(classModel, p, rootModel) == null && !typeSupport.isBuiltIn(pTypeName)) {
                hasDynamic = true;
            }
            if (!p.isOptional()) {
                ExecutableElement localRes = findLocalResolver(classModel, p, rootModel);
                if (localRes != null) {
                    staticRequiredCount += getLocalResolverMinWidth(localRes, method);
                } else {
                    staticRequiredCount += typeSupport.getWidth(pTypeName);
                }
            }
        }

        int totalRequired = staticRequiredCount + initialOffset;
        if (staticRequiredCount > 0) {
            methodSpec.beginControlFlow("if ($L.length < $L)", argsVar, totalRequired)
                    .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                            CommandException.class, "usage", "Usage: %s", getUsage(method, classModel))
                    .endControlFlow();
        }

        if (!method.getParameters().isEmpty()) {
            if (hasDynamic) {
                methodSpec.addStatement("int[] argIdxHolder = { $L }", initialOffset);
            } else {
                methodSpec.addStatement("int argIdx = $L", initialOffset);
            }
        }
        String argIdxVar = hasDynamic ? "argIdxHolder[0]" : "argIdx";

        // 4. Resolve Parameters
        List<String> paramNames = new ArrayList<>();
        paramNames.add(senderVarName);

        for (int i = 0; i < method.getParameters().size(); i++) {
            ParameterModel p = method.getParameters().get(i);
            String varName = "param_" + i;
            paramNames.add(varName);

            resolveParameter(methodSpec, classModel, method, rootModel, p, varName, senderVarName, argsVar, argIdxVar, hasDynamic, i);
            runParameterAnnotationHandlers(p.getElement(), varName, instanceVar, senderVarName, methodSpec);
        }

        // 5. Invoke Target Method
        CodeBlock.Builder callBuilder = CodeBlock.builder().add("$L.$L(", instanceVar, method.getMethodName());
        for (int i = 0; i < paramNames.size(); i++) {
            if (i > 0) callBuilder.add(", ");
            callBuilder.add("$L", paramNames.get(i));
        }
        callBuilder.add(")");
        methodSpec.addStatement(callBuilder.build());
    }

    public void resolveSender(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, String senderVarName, ParameterModel senderParam, TypeName senderParamTypeName) {
        Resolve resolveAnn = senderParam.getElement().getAnnotation(Resolve.class);

        if (resolveAnn != null && !resolveAnn.value().isEmpty()) {
            ExecutableElement senderResolver = findLocalResolver(classModel, senderParam, rootModel);
            if (senderResolver != null) {
                String resolverInstanceExpr = getResolverInstanceExpr(senderResolver, classModel, rootModel);
                if (resolverInstanceExpr == null) {
                    methodSpec.addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                            senderParamTypeName, senderVarName, senderParamTypeName, senderParamTypeName);
                } else {
                    String resolverMethodName = senderResolver.getSimpleName().toString();
                    int resolverParamCount = senderResolver.getParameters().size();
                    String resolveExpr;
                    if (resolverParamCount == 0) {
                        resolveExpr = String.format("%s.%s()", resolverInstanceExpr, resolverMethodName);
                    } else if (resolverParamCount == 1) {
                        resolveExpr = String.format("%s.%s(%s)", resolverInstanceExpr, resolverMethodName, "sender");
                    } else if (resolverParamCount == 2) {
                        resolveExpr = String.format("%s.%s(%s, %s)", resolverInstanceExpr, resolverMethodName, "sender", "sender");
                    } else {
                        resolveExpr = String.format("%s.%s(%s, %s, %s)", resolverInstanceExpr, resolverMethodName, "sender", "new String[0]", "sender");
                    }
                    TypeName resolverReturnType = TypeName.get(senderResolver.getReturnType());
                    if (resolverReturnType.equals(senderParamTypeName)) {
                        methodSpec.addStatement("$T $L = $L", senderParamTypeName, senderVarName, resolveExpr);
                    } else {
                        methodSpec.addStatement("$T $L = ($T) $L", senderParamTypeName, senderVarName, senderParamTypeName, resolveExpr);
                    }
                }
            } else {
                methodSpec.addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                        senderParamTypeName, senderVarName, senderParamTypeName, senderParamTypeName);
            }
        } else if (resolveAnn != null) {
            methodSpec.addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                    senderParamTypeName, senderVarName, senderParamTypeName, senderParamTypeName);
        } else {
            if (!senderVarName.equals("sender")) {
                if (!isSenderBaseType(senderParamTypeName)) {
                    if (isSenderType(senderParamTypeName)) {
                        String castMethodName = "as" + getSimpleName(senderParamTypeName);
                        methodSpec.addStatement("$T $L = $L(sender)", senderParamTypeName, senderVarName, castMethodName);
                    } else {
                        methodSpec.addStatement("$T $L = ($T) manager.resolveSender($T.class, sender)",
                                senderParamTypeName, senderVarName, senderParamTypeName, senderParamTypeName);
                    }
                } else {
                    methodSpec.addStatement("$T $L = sender", getSenderTypeName(), senderVarName);
                }
            }
        }
    }

    public void resolveParameter(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, ParameterModel p, String varName, String senderVarName, String argsVar, String argIdxVar, boolean hasDynamic, int paramIndex) {
        TypeName pTypeName = TypeName.get(p.getType());

        // 1. Resolver model from @Resolve("name")
        Resolve resolveAnn = p.getElement().getAnnotation(Resolve.class);
        if (resolveAnn != null && classModel.getResolverMethod(resolveAnn.value()) != null) {
            resolveResolverParameters(methodSpec, classModel, method, rootModel, classModel.getResolverMethod(resolveAnn.value()), varName, senderVarName, argsVar, argIdxVar, p, hasDynamic, paramIndex);
            return;
        }

        // 2. Local Resolver Method
        ExecutableElement localResolver = findLocalResolver(classModel, p, rootModel);
        if (localResolver != null) {
            resolveLocalResolverParameter(methodSpec, classModel, method, p, pTypeName, varName, localResolver, rootModel, senderVarName, argsVar, argIdxVar, hasDynamic, paramIndex);
            return;
        }

        // 3. Greedy Primitive Array
        if (p.isGreedy() && pTypeName.toString().endsWith("[]")) {
            String componentType = pTypeName.toString().replace("[]", "");
            String boxedComponent = TypeSupport.getWrapperName(componentType);
            int lastDot = boxedComponent.lastIndexOf('.');
            String packageName = boxedComponent.substring(0, lastDot);
            String simpleName = boxedComponent.substring(lastDot + 1);

            if (hasDynamic) {
                methodSpec.beginControlFlow("if ($L + 1 > $L.length)", argIdxVar, argsVar)
                        .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                                CommandException.class, "missing-argument", "Missing arguments for parameter: %s", p.getName())
                        .endControlFlow();
            }

            methodSpec.addStatement("$T[] $L_raw = $T.copyOfRange($L, $L, $L.length)", String.class, varName, Arrays.class, argsVar, argIdxVar, argsVar);
            methodSpec.addStatement("$T $L = new $L[$L_raw.length]", pTypeName, varName, componentType, varName);
            methodSpec.beginControlFlow("for (int j = 0; j < $L_raw.length; j++)", varName);
            if (TypeSupport.isNumericType(componentType)) {
                String parseMethod = "parse" + Character.toUpperCase(componentType.charAt(0)) + componentType.substring(1);
                methodSpec.addStatement("$L[j] = $T.$L($L_raw[j])", varName, ClassName.get(packageName, simpleName), parseMethod, varName);
            } else {
                methodSpec.addStatement("$L[j] = $T.valueOf($L_raw[j])", varName, ClassName.get(packageName, simpleName), varName);
            }
            methodSpec.endControlFlow();
            return;
        }

        // 4. Greedy String or Object
        if (p.isGreedy()) {
            if (pTypeName.toString().equals("java.lang.String")) {
                if (p.isOptional()) {
                    String defVal = p.getDefaultValue() == null ? "null" : CodeBlock.of("$S", p.getDefaultValue()).toString();
                    methodSpec.addStatement("$T $L = $L >= $L.length ? $L : String.join($S, $T.copyOfRange($L, $L, $L.length))",
                            pTypeName, varName, argIdxVar, argsVar, defVal, " ", Arrays.class, argsVar, argIdxVar, argsVar);
                } else {
                    methodSpec.addStatement("$T $L = String.join($S, $T.copyOfRange($L, $L, $L.length))",
                            pTypeName, varName, " ", Arrays.class, argsVar, argIdxVar, argsVar);
                }
            } else {
                CodeBlock greedyExpr = CodeBlock.of("String.join($S, $T.copyOfRange($L, $L, $L.length))",
                        " ", Arrays.class, argsVar, argIdxVar, argsVar);
                CodeBlock parseExpr = typeSupport.parseExpr(pTypeName, greedyExpr.toString());
                if (parseExpr != null) {
                    methodSpec.addStatement("$T $L = $L", pTypeName, varName, parseExpr);
                } else {
                    methodSpec.addStatement("String greedy_$L = $L", paramIndex, greedyExpr);
                    methodSpec.addStatement("$T $L", pTypeName, varName);
                    typeSupport.emitParse(methodSpec, pTypeName, varName, "greedy_" + paramIndex);
                }
            }
            return;
        }

        // 5. Built-in or Platform Types
        if (typeSupport.isBuiltIn(pTypeName)) {
            int width = typeSupport.getWidth(pTypeName);
            if (hasDynamic && !p.isOptional()) {
                methodSpec.beginControlFlow("if ($L + $L > $L.length)", argIdxVar, width, argsVar)
                        .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                                CommandException.class, "missing-argument", "Missing arguments for parameter: %s", p.getName())
                        .endControlFlow();
            }

            if (width > 1) {
                // Multi-arg platform type (e.g. Location)
                if (p.isOptional()) {
                    methodSpec.beginControlFlow("if ($L + $L > $L.length)", argIdxVar, width, argsVar);
                    methodSpec.addStatement("$T $L = null", pTypeName, varName);
                    methodSpec.nextControlFlow("else");
                    typeSupport.emitPlatformMultiResolution(methodSpec, pTypeName, varName, argsVar, argIdxVar, senderVarName, String.valueOf(paramIndex));
                    methodSpec.addStatement("$L += $L", argIdxVar, width);
                    methodSpec.endControlFlow();
                } else {
                    methodSpec.addStatement("$T $L", pTypeName, varName);
                    typeSupport.emitPlatformMultiResolution(methodSpec, pTypeName, varName, argsVar, argIdxVar, senderVarName, String.valueOf(paramIndex));
                    methodSpec.addStatement("$L += $L", argIdxVar, width);
                }
            } else {
                // Single-arg type
                CodeBlock parseExpr = typeSupport.parseExpr(pTypeName, argsVar + "[" + argIdxVar + "++]");
                if (p.isOptional()) {
                    CodeBlock defLit = typeSupport.literal(pTypeName, p.getDefaultValue());
                    if (defLit == null) defLit = CodeBlock.of("null");
                    methodSpec.addStatement("$T $L = $L >= $L.length ? $L : $L",
                            pTypeName, varName, argIdxVar, argsVar, defLit, parseExpr);
                } else {
                    methodSpec.addStatement("$T $L = $L", pTypeName, varName, parseExpr);
                }
            }
            return;
        }

        // 6. Dynamic Manager Resolver (Fallback)
        methodSpec.addStatement("$T $L", pTypeName, varName);
        String defValLiteral = p.getDefaultValue() == null ? "null" : CodeBlock.of("$S", p.getDefaultValue()).toString();
        methodSpec.addStatement("$L = manager.resolveParameter(sender, $T.class, $L, $L, $S, $L, $L)",
                varName, pTypeName.isPrimitive() ? pTypeName.box() : pTypeName,
                argsVar, hasDynamic ? "argIdxHolder" : "new int[]{" + argIdxVar + "}", p.getName(), p.isOptional(), defValLiteral);
    }

    private void resolveLocalResolverParameter(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, ParameterModel p, TypeName pTypeName, String varName, ExecutableElement localResolver, CommandModel rootModel, String senderVarName, String argsVar, String argIdxVar, boolean hasDynamic, int paramIndex) {
        int minWidth = getLocalResolverMinWidth(localResolver, method);
        int maxWidth = getLocalResolverMaxWidth(localResolver, method);

        if (hasDynamic && minWidth > 0) {
            methodSpec.beginControlFlow("if ($L + $L > $L.length)", argIdxVar, minWidth, argsVar)
                    .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                            CommandException.class, "missing-argument", "Missing arguments for parameter: %s", p.getName())
                    .endControlFlow();
        }

        List<? extends VariableElement> resolverParams = localResolver.getParameters();
        int resolverStartIndex = firstParamIsSender(localResolver, method) ? 1 : 0;
        List<String> resolverArgVarNames = new ArrayList<>();
        int resolverArgIdx = 0;

        for (int j = resolverStartIndex; j < resolverParams.size(); j++) {
            VariableElement rp = resolverParams.get(j);
            TypeName rpTypeName = TypeName.get(rp.asType());
            String rpVarName = varName + "_rp_" + resolverArgIdx;
            resolverArgVarNames.add(rpVarName);

            if (isSenderParam(rpTypeName, method)) {
                methodSpec.addStatement("$T $L = $L", rpTypeName, rpVarName,
                        getResolverSenderExpression(localResolver, method.getSenderParameter().getName(), senderVarName, TypeName.get(method.getSenderType())));
            } else {
                Default defaultAnn = rp.getAnnotation(Default.class);
                boolean isOptional = defaultAnn != null;
                String defaultValue = (defaultAnn != null && !defaultAnn.value().isEmpty()) ? defaultAnn.value() : null;
                if (!isOptional && p.isOptional()) {
                    isOptional = true;
                    defaultValue = p.getDefaultValue();
                }

                CodeBlock parseExpr = typeSupport.parseExpr(rpTypeName, argsVar + "[" + argIdxVar + "++]");
                if (isOptional) {
                    CodeBlock defLit = typeSupport.literal(rpTypeName, defaultValue);
                    if (defLit == null) defLit = CodeBlock.of("null");
                    methodSpec.addStatement("$T $L = $L >= $L.length ? $L : $L",
                            rpTypeName, rpVarName, argIdxVar, argsVar, defLit, parseExpr);
                } else {
                    methodSpec.addStatement("$T $L = $L", rpTypeName, rpVarName, parseExpr);
                }
            }
            resolverArgIdx++;
        }

        // Run SPI handlers on resolver parameters
        resolverArgIdx = 0;
        String instanceVarExpr = getInstanceVarExpression(classModel, rootModel);
        for (int j = resolverStartIndex; j < resolverParams.size(); j++) {
            VariableElement rp = resolverParams.get(j);
            TypeName rpTypeName = TypeName.get(rp.asType());
            if (isSenderParam(rpTypeName, method)) continue;

            String rpVarName = varName + "_rp_" + resolverArgIdx;
            runParameterAnnotationHandlers(rp, rpVarName, instanceVarExpr, senderVarName, methodSpec);
            resolverArgIdx++;
        }

        // Invoke resolver
        String resolverInstanceExpr = getResolverInstanceExpr(localResolver, classModel, rootModel);
        if (resolverInstanceExpr == null) {
            methodSpec.addStatement("$T $L = null", pTypeName, varName);
            return;
        }

        CodeBlock.Builder resolveCall = CodeBlock.builder().add("$L.$L(", resolverInstanceExpr, localResolver.getSimpleName());
        if (firstParamIsSender(localResolver, method)) {
            resolveCall.add("$L", getResolverSenderExpression(localResolver, method.getSenderParameter().getName(), senderVarName, TypeName.get(method.getSenderType())));
            if (!resolverArgVarNames.isEmpty()) resolveCall.add(", ");
        }
        for (int j = 0; j < resolverArgVarNames.size(); j++) {
            if (j > 0) resolveCall.add(", ");
            resolveCall.add("$L", resolverArgVarNames.get(j));
        }
        resolveCall.add(")");
        TypeName localResolverReturnType = TypeName.get(localResolver.getReturnType());
        if (localResolverReturnType.equals(pTypeName)) {
            methodSpec.addStatement("$T $L = $L", pTypeName, varName, resolveCall.build());
        } else {
            methodSpec.addStatement("$T $L = ($T) $L", pTypeName, varName, pTypeName, resolveCall.build());
        }
    }

    public void resolveResolverParameters(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, MethodModel resolverModel, String varName, String senderVarName, String argsVar, String argIdxVar, ParameterModel parentParam, boolean hasDynamic, int paramIndex) {
        ExecutableElement resolverElement = resolverModel.getElement();
        TypeName returnType = TypeName.get(resolverElement.getReturnType());

        boolean includeSender = !resolverModel.getParameters().isEmpty() && isSenderParam(TypeName.get(resolverModel.getParameters().get(0).getType()), method);
        List<String> argNames = new ArrayList<>();

        for (int i = 0; i < resolverModel.getParameters().size(); i++) {
            ParameterModel rp = resolverModel.getParameters().get(i);
            if (isSenderParam(TypeName.get(rp.getType()), method)) continue;
            String rpVarName = varName + "_rp_" + i;
            argNames.add(rpVarName);

            ParameterModel rpToResolve = rp;
            if (!rp.isOptional() && parentParam != null && parentParam.isOptional()) {
                rpToResolve = new ParameterModel(
                        rp.getName(), rp.getType(), rp.isGreedy(), true,
                        parentParam.getDefaultValue(), rp.getSuggestProvider(), rp.getElement()
                );
            }

            resolveParameter(methodSpec, classModel, method, rootModel, rpToResolve, rpVarName, senderVarName, argsVar, argIdxVar, hasDynamic, i);
            runParameterAnnotationHandlers(rp.getElement(), rpVarName, getInstanceVarExpression(classModel, rootModel), senderVarName, methodSpec);
        }

        String resolverSenderExpr = getResolverSenderExpression(resolverElement, method.getSenderParameter().getName(), senderVarName, TypeName.get(method.getSenderType()));
        generateResolverInvocation(methodSpec, resolverElement, classModel, rootModel, returnType, varName, resolverSenderExpr, argNames, includeSender);
    }

    public void generateResolverInvocation(MethodSpec.Builder methodSpec, ExecutableElement localResolver, CommandModel classModel, CommandModel rootModel, TypeName pTypeName, String varName, String senderVarName, List<String> resolverArgVarNames, boolean includeSender) {
        String resolverInstanceExpr = getResolverInstanceExpr(localResolver, classModel, rootModel);
        if (resolverInstanceExpr == null) {
            methodSpec.addStatement("$T $L = $L", pTypeName, varName, pTypeName.isPrimitive() ? "false" : "null");
            return;
        }
        CodeBlock.Builder resolveCall = CodeBlock.builder().add("$L.$L(", resolverInstanceExpr, localResolver.getSimpleName());
        if (includeSender) {
            resolveCall.add("$L", senderVarName);
            if (!resolverArgVarNames.isEmpty()) resolveCall.add(", ");
        }
        for (int j = 0; j < resolverArgVarNames.size(); j++) {
            if (j > 0) resolveCall.add(", ");
            resolveCall.add("$L", resolverArgVarNames.get(j));
        }
        resolveCall.add(")");
        TypeName resolverReturnType = TypeName.get(localResolver.getReturnType());
        if (resolverReturnType.equals(pTypeName)) {
            methodSpec.addStatement("$T $L = $L", pTypeName, varName, resolveCall.build());
        } else {
            methodSpec.addStatement("$T $L = ($T) $L", pTypeName, varName, pTypeName, resolveCall.build());
        }
    }

    // ── Tab Completion & Suggestions ──

    public boolean isParamDirectSuggestionAvailable(ParameterModel p, CommandModel classModel) {
        if (p.getSuggestProvider() != null) return true;
        TypeName typeName = TypeName.get(p.getType());
        if (typeName.toString().equals("boolean") || typeName.toString().equals("java.lang.Boolean")) return true;
        if (isPlatformBuiltInType(typeName)) return true;

        Resolve resolveAnn = p.getElement().getAnnotation(Resolve.class);
        if (resolveAnn != null && !resolveAnn.value().isEmpty()) {
            return false; // Resolver params generate their own suggestions
        }
        if (findLocalResolver(classModel, p, classModel) != null) {
            return false; // Flattened into local resolver params
        }

        return !typeSupport.isBuiltIn(typeName);
    }

    public boolean isResolverParamSuggestionAvailable(VariableElement rp) {
        if (rp.getAnnotation(Suggest.class) != null) return true;
        TypeName typeName = TypeName.get(rp.asType());
        if (typeName.toString().equals("boolean") || typeName.toString().equals("java.lang.Boolean")) return true;
        if (isPlatformBuiltInType(typeName)) return true;
        return !typeSupport.isBuiltIn(typeName);
    }

    public boolean isParamSuggestionAvailable(ParameterModel p, CommandModel classModel) {
        if (p.getSuggestProvider() != null) return true;
        TypeName typeName = TypeName.get(p.getType());
        if (typeName.toString().equals("boolean") || typeName.toString().equals("java.lang.Boolean")) return true;
        if (isPlatformBuiltInType(typeName)) return true;

        Resolve resolveAnn = p.getElement().getAnnotation(Resolve.class);
        if (resolveAnn != null && !resolveAnn.value().isEmpty()) {
            MethodModel resolverModel = classModel.getResolverMethod(resolveAnn.value());
            if (resolverModel != null) {
                for (ParameterModel rp : resolverModel.getParameters()) {
                    if (isSenderParam(TypeName.get(rp.getType()), null)) continue;
                    if (isParamSuggestionAvailable(rp, classModel)) return true;
                }
                return false;
            }
        }
        ExecutableElement localRes = findLocalResolver(classModel, p, classModel);
        if (localRes != null) {
            for (VariableElement rp : localRes.getParameters()) {
                if (isSenderParam(TypeName.get(rp.asType()), null)) continue;
                if (isResolverParamSuggestionAvailable(rp)) return true;
            }
            return false;
        }

        return !typeSupport.isBuiltIn(typeName);
    }

    public boolean hasAnySuggestions(MethodModel method, CommandModel classModel) {
        for (ParameterModel p : method.getParameters()) {
            if (p == method.getSenderParameter()) continue;
            if (isParamSuggestionAvailable(p, classModel)) return true;
        }
        return false;
    }

    public void buildSuggestionRouting(MethodSpec.Builder methodSpec, CommandModel model, String argsVar, String instanceVar, CommandModel rootModel) {
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
                if (isParamSuggestionAvailable(p0, model)) {
                    String helperName = getParameterSuggestionMethodName(model, model.getDefaultMethod(), 0);
                    methodSpec.addStatement("suggestions.addAll($L(sender, $L))", helperName, argsVar);
                }
            }

            methodSpec.addStatement("return $T.filterSuggestions(suggestions, current)", ClassName.get("io.github.projectunified.craftcommand", "CommandManager"));
            methodSpec.endControlFlow();

            // Routing for args.length > 1
            boolean hasSubSuggestions = !model.getNestedSubcommands().isEmpty() ||
                    model.getSubcommands().stream().anyMatch(sub -> hasAnySuggestions(sub, model));

            if (hasSubSuggestions) {
                methodSpec.beginControlFlow("if ($L.length > 1)", argsVar);
                methodSpec.addStatement("String sub = $L[0].toLowerCase()", argsVar);
                methodSpec.beginControlFlow("switch (sub)");

                // Route to nested subcommand classes
                for (CommandModel child : model.getNestedSubcommands()) {
                    for (String name : collectLoweredNames(child)) {
                        methodSpec.addCode("case $S:\n", name);
                    }
                    methodSpec.addCode("{\n");
                    String childHelperName = Naming.suggestHelper(child.getClassName());
                    methodSpec.addStatement("$T subArgs = $T.copyOfRange($L, 1, $L.length)", String[].class, Arrays.class, argsVar, argsVar);
                    methodSpec.addStatement("return $L(sender, subArgs)", childHelperName);
                    methodSpec.addCode("}\n");
                }

                // Route to subcommand methods ONLY IF THEY HAVE SUGGESTIONS
                for (MethodModel sub : model.getSubcommands()) {
                    if (!hasAnySuggestions(sub, model)) {
                        continue;
                    }
                    for (String name : collectLoweredNames(sub)) {
                        methodSpec.addCode("case $S:\n", name);
                    }
                    methodSpec.addCode("{\n");
                    methodSpec.addStatement("$T subArgs = $T.copyOfRange($L, 1, $L.length)", String[].class, Arrays.class, argsVar, argsVar);
                    buildSubcommandSuggestionRouting(methodSpec, model, sub, "subArgs");
                    methodSpec.addCode("}\n");
                }

                methodSpec.endControlFlow(); // switch
                methodSpec.endControlFlow(); // if
            }
        }

        // Default command tab complete
        if (model.getDefaultMethod() != null && hasAnySuggestions(model.getDefaultMethod(), model)) {
            buildSubcommandSuggestionRouting(methodSpec, model, model.getDefaultMethod(), argsVar);
        } else {
            methodSpec.addStatement("return $T.emptyList()", Collections.class);
        }
    }

    protected void buildSubcommandSuggestionRouting(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, String argsVar) {
        int paramCount = method.getParameters().size();
        if (paramCount == 0 || !hasAnySuggestions(method, classModel)) {
            methodSpec.addStatement("return $T.emptyList()", Collections.class);
            return;
        }

        methodSpec.addStatement("if ($L.length == 0) return $T.emptyList()", argsVar, Collections.class);
        methodSpec.addStatement("int index = $L.length - 1", argsVar);
        methodSpec.addStatement("int tempIdx = 0");

        for (int i = 0; i < paramCount; i++) {
            ParameterModel p = method.getParameters().get(i);
            TypeName pTypeName = TypeName.get(p.getType());

            ExecutableElement localResolver = findLocalResolver(classModel, p, classModel);

            if (localResolver != null) {
                int resolverStartIndex = firstParamIsSender(localResolver, method) ? 1 : 0;
                List<? extends VariableElement> resolverParams = localResolver.getParameters();

                Resolve resolveAnn = p.getElement().getAnnotation(Resolve.class);
                String resolverName = (resolveAnn != null && !resolveAnn.value().isEmpty())
                        ? resolveAnn.value()
                        : localResolver.getSimpleName().toString();

                for (int ri = resolverStartIndex; ri < resolverParams.size(); ri++) {
                    VariableElement rp = resolverParams.get(ri);
                    boolean hasSugg = isResolverParamSuggestionAvailable(rp);
                    if (hasSugg) {
                        String helperName = getResolverParamSuggestionMethodName(classModel, method, resolverName, ri - resolverStartIndex);
                        methodSpec.beginControlFlow("if (index < tempIdx + 1)")
                                .addStatement("return $L(sender, $L)", helperName, argsVar)
                                .endControlFlow();
                    }
                    methodSpec.addStatement("tempIdx += 1");
                }
            } else {
                int width = getBuiltInWidth(pTypeName);
                boolean isDynamic = !typeSupport.isBuiltIn(pTypeName);
                String widthExpr;
                if (isDynamic) {
                    TypeName boxedType = pTypeName.isPrimitive() ? pTypeName.box() : pTypeName;
                    widthExpr = "manager.getResolver(" + boxedType + ".class).getWidth()";
                } else {
                    widthExpr = String.valueOf(width);
                }

                if (isParamDirectSuggestionAvailable(p, classModel)) {
                    String helperName = getParameterSuggestionMethodName(classModel, method, i);
                    methodSpec.beginControlFlow("if (index < tempIdx + $L)", widthExpr)
                            .addStatement("return $L(sender, $L)", helperName, argsVar)
                            .endControlFlow();
                }
                methodSpec.addStatement("tempIdx += $L", widthExpr);
            }
        }
        methodSpec.addStatement("return $T.emptyList()", Collections.class);
    }

    protected void buildParameterSuggestions(TypeSpec.Builder typeSpec, CommandModel model, CommandModel rootModel) {
        if (model.getDefaultMethod() != null) {
            for (int i = 0; i < model.getDefaultMethod().getParameters().size(); i++) {
                ParameterModel p = model.getDefaultMethod().getParameters().get(i);
                if (isParamDirectSuggestionAvailable(p, model)) {
                    typeSpec.addMethod(buildParameterSuggestionHelper(model, model.getDefaultMethod(), p, i, rootModel));
                }
                buildResolverParamSuggestions(typeSpec, model, model.getDefaultMethod(), p, rootModel);
            }
        }
        for (MethodModel sub : model.getSubcommands()) {
            for (int i = 0; i < sub.getParameters().size(); i++) {
                ParameterModel p = sub.getParameters().get(i);
                if (isParamDirectSuggestionAvailable(p, model)) {
                    typeSpec.addMethod(buildParameterSuggestionHelper(model, sub, p, i, rootModel));
                }
                buildResolverParamSuggestions(typeSpec, model, sub, p, rootModel);
            }
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            buildParameterSuggestions(typeSpec, child, rootModel);
        }
    }

    private void buildResolverParamSuggestions(TypeSpec.Builder typeSpec, CommandModel classModel, MethodModel method, ParameterModel p, CommandModel rootModel) {
        Resolve resolveAnn = p.getElement().getAnnotation(Resolve.class);
        if (resolveAnn == null || resolveAnn.value().isEmpty()) return;
        MethodModel resolverModel = classModel.getResolverMethod(resolveAnn.value());
        if (resolverModel == null) return;

        for (int i = 0; i < resolverModel.getParameters().size(); i++) {
            ParameterModel rp = resolverModel.getParameters().get(i);
            if (isResolverParamSuggestionAvailable(rp.getElement())) {
                String helperName = getResolverParamSuggestionMethodName(classModel, method, resolveAnn.value(), i);
                typeSpec.addMethod(buildResolverParamSuggestionHelper(classModel, method, rp, helperName, rootModel));
            }
        }
    }

    protected String getParameterSuggestionMethodName(CommandModel classModel, MethodModel method, int index) {
        String classPath = Naming.classPath(classModel.getClassName());
        String methodOrDefault = method.isDefault() ? "default" : method.getSubcommandName();
        return Naming.suggestMethod(classPath, methodOrDefault, index);
    }

    protected String getResolverParamSuggestionMethodName(CommandModel classModel, MethodModel method, String resolverName, int index) {
        String classPath = Naming.classPath(classModel.getClassName());
        String methodOrDefault = method.isDefault() ? "default" : method.getSubcommandName();
        return Naming.suggestMethod(classPath, methodOrDefault + "_" + resolverName, index);
    }

    protected MethodSpec buildResolverParamSuggestionHelper(CommandModel classModel, MethodModel method, ParameterModel rp, String helperName, CommandModel rootModel) {
        return buildSuggestionHelperInternal(classModel, method, rp.getSuggestProvider(),
                getInstanceVarExpression(classModel, rootModel), TypeName.get(rp.getType()),
                rp.getName(), method.getMethodName(), 0, helperName, "resolver param", rootModel);
    }

    protected MethodSpec buildParameterSuggestionHelper(CommandModel classModel, MethodModel method, ParameterModel p, int index, CommandModel rootModel) {
        int tempIdx = 0;
        for (int j = 0; j < index; j++) {
            ParameterModel prev = method.getParameters().get(j);
            tempIdx += getBuiltInWidth(TypeName.get(prev.getType()));
        }
        String helperName = getParameterSuggestionMethodName(classModel, method, index);
        return buildSuggestionHelperInternal(classModel, method, p.getSuggestProvider(),
                getInstanceVarExpression(classModel, rootModel), TypeName.get(p.getType()),
                p.getName(), method.getMethodName(), tempIdx, helperName, "parameter", rootModel);
    }

    private MethodSpec buildSuggestionHelperInternal(CommandModel classModel, MethodModel method, String provider, String instanceExpr, TypeName pTypeName, String paramName, String methodName, int tempIdxOffset, String helperName, String paramLabel, CommandModel rootModel) {
        MethodSpec.Builder methodSpec = MethodSpec.methodBuilder(helperName)
                .addJavadoc("Gets suggestions for " + paramLabel + " {@code $L} of method {@code $L}.\n\n"
                        + "@param sender the command sender\n"
                        + "@param args the command arguments\n"
                        + "@return the list of suggestions\n", paramName, methodName)
                .addModifiers(Modifier.PRIVATE)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addParameter(getSenderTypeName(), "sender")
                .addParameter(String[].class, "args");

        String senderCastVar = "sender";
        boolean needsSenderCast = false;
        if (provider != null) {
            TypeElement typeElement = classModel.getElement();
            ExecutableElement suggestMethod = findSuggestMethod(typeElement, provider);
            if (suggestMethod != null && !suggestMethod.getParameters().isEmpty()) {
                VariableElement firstParam = suggestMethod.getParameters().get(0);
                TypeMirror firstParamType = firstParam.asType();
                TypeName firstParamTypeName = TypeName.get(firstParamType);

                if (!isStringArray(firstParamType) && !firstParamTypeName.toString().equals(getSenderTypeName().toString())) {
                    Resolve resolveAnn = firstParam.getAnnotation(Resolve.class);
                    if (resolveAnn != null) {
                        if (!resolveAnn.value().isEmpty()) {
                            ExecutableElement resolver = resolverLookup.findMethod(typeElement, resolveAnn.value());
                            if (resolver != null) {
                                String resolverInstanceExpr = getInstanceVarExpression(classModel, rootModel);
                                String resolveExpr = String.format("%s.%s(%s)", resolverInstanceExpr, resolver.getSimpleName().toString(), "sender");
                                methodSpec.addStatement("$T senderCast = ($T) $L", firstParamTypeName, firstParamTypeName, resolveExpr);
                                senderCastVar = "senderCast";
                            }
                        } else {
                            methodSpec.addStatement("$T senderCast = ($T) manager.resolveSender($T.class, sender)", firstParamTypeName, firstParamTypeName, firstParamTypeName);
                            senderCastVar = "senderCast";
                        }
                    } else if (isSenderType(firstParamTypeName)) {
                        String castMethodName = "as" + getSimpleName(firstParamTypeName);
                        methodSpec.beginControlFlow("try");
                        methodSpec.addStatement("$T senderCast = $L($L)", firstParamTypeName, castMethodName, CodeBlock.of("sender"));
                        senderCastVar = "senderCast";
                        needsSenderCast = true;
                    }
                }
            }
        }

        methodSpec.addStatement("int index = $L.length - 1", "args");
        methodSpec.addStatement("String currentStr = $L[index]", "args");

        if (provider != null) {
            TypeElement typeElement = classModel.getElement();
            ExecutableElement suggestMethod = findSuggestMethod(typeElement, provider);
            if (suggestMethod != null) {
                int argCount = suggestMethod.getParameters().size();
                if (argCount == 0) {
                    methodSpec.addStatement("return $T.filterSuggestions($L.$L(), $L)",
                            ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider, "currentStr");
                } else if (argCount == 1) {
                    TypeMirror firstParamType = suggestMethod.getParameters().get(0).asType();
                    if (!isStringArray(firstParamType)) {
                        methodSpec.addStatement("return $T.filterSuggestions($L.$L($L), $L)",
                                ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider, senderCastVar, "currentStr");
                    } else {
                        methodSpec.addStatement("return $T.filterSuggestions($L.$L(new String[]{currentStr}), currentStr)",
                                ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider);
                    }
                } else if (argCount == 2) {
                    TypeMirror firstParamType = suggestMethod.getParameters().get(0).asType();
                    if (!isStringArray(firstParamType)) {
                        methodSpec.addStatement("return $T.filterSuggestions($L.$L($L, new String[]{currentStr}), currentStr)",
                                ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider, senderCastVar);
                    } else {
                        methodSpec.addStatement("return $T.filterSuggestions($L.$L(new String[]{currentStr}, $L), currentStr)",
                                ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider, "args");
                    }
                } else if (argCount == 3) {
                    methodSpec.addStatement("return $T.filterSuggestions($L.$L($L, new String[]{currentStr}, $L), currentStr)",
                            ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider, senderCastVar, "args");
                }
            } else if (isField(typeElement, provider)) {
                methodSpec.addStatement("return $T.filterSuggestions($L.$L, currentStr)",
                        ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, provider);
            } else {
                processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR, "Could not find field or method '" + provider + "' for suggestions in " + classModel.getClassName().simpleName());
                methodSpec.addStatement("return $T.emptyList()", Collections.class);
            }
        } else {
            if (pTypeName.toString().equals("boolean") || pTypeName.toString().equals("java.lang.Boolean")) {
                methodSpec.addStatement("return suggestBoolean(currentStr)");
            } else if (isPlatformBuiltInType(pTypeName)) {
                typeSupport.emitPlatformSuggestions(methodSpec, pTypeName, senderCastVar, "args", "currentStr", String.valueOf(tempIdxOffset));
            } else if (typeSupport.isBuiltIn(pTypeName)) {
                methodSpec.addStatement("return $T.emptyList()", Collections.class);
            } else if (getSenderTypeName().toString().equals("java.lang.Object")) {
                methodSpec.addStatement("return manager.getResolver($T.class).suggest(sender, new String[]{currentStr}, args)",
                        pTypeName.isPrimitive() ? pTypeName.box() : pTypeName);
            } else {
                methodSpec.addStatement("return manager.getResolver($T.class).suggest(($T) sender, new String[]{currentStr}, args)",
                        pTypeName.isPrimitive() ? pTypeName.box() : pTypeName, getSenderTypeName());
            }
        }

        if (needsSenderCast) {
            methodSpec.nextControlFlow("catch ($T e)", CommandException.class)
                    .addStatement("return $T.emptyList()", Collections.class)
                    .endControlFlow();
        }

        return methodSpec.build();
    }

    // ── Helper Generators (Booleans & Casts) ──

    protected void buildBooleanSuggestionHelper(TypeSpec.Builder typeSpec, CommandModel model) {
        if (!hasBooleanParameter(model)) return;

        typeSpec.addMethod(MethodSpec.methodBuilder("suggestBoolean")
                .addJavadoc("Suggests boolean values matching the current input (case-insensitive).\n\n"
                        + "@param current the current user input\n"
                        + "@return a list of matching boolean suggestions\n")
                .addModifiers(Modifier.PRIVATE)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addParameter(String.class, "current")
                .addStatement("if (current == null || current.isEmpty()) return $T.asList(\"true\", \"false\")", Arrays.class)
                .addStatement("$T list = new $T<>()", ParameterizedTypeName.get(List.class, String.class), ArrayList.class)
                .addStatement("String lower = current.toLowerCase()")
                .addStatement("if (\"true\".startsWith(lower)) list.add(\"true\")")
                .addStatement("if (\"false\".startsWith(lower)) list.add(\"false\")")
                .addStatement("return list")
                .build());
    }

    protected void buildSenderCastHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        for (TypeName type : getSenderTypesToCast(model)) {
            if (type.toString().equals(getSenderTypeName().toString())) continue;
            String methodName = "as" + getSimpleName(type);
            typeSpec.addMethod(MethodSpec.methodBuilder(methodName)
                    .addJavadoc("Casts the command sender to {@link $T} after verification.\n\n"
                            + "@param sender the raw command sender\n"
                            + "@return the casted sender\n"
                            + "@throws CommandException if the sender is not of the expected type\n", type)
                    .addModifiers(Modifier.PRIVATE)
                    .returns(type)
                    .addParameter(getSenderTypeName(), "sender")
                    .beginControlFlow("if (!($L instanceof $T))", getSenderExpression("sender"), type)
                    .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                            CommandException.class, "invalid-sender", "Only %s can execute this command.", getSimpleName(type))
                    .endControlFlow()
                    .addStatement("return ($T) $L", type, getSenderExpression("sender"))
                    .build());
        }
    }

    // ── Command Metadata (CommandInfo) ──

    protected void buildCommandInfo(TypeSpec.Builder typeSpec, CommandModel model) {
        ClassName commandInfoClass = ClassName.get("io.github.projectunified.craftcommand", "CommandInfo");
        boolean hasI18n = hasDescriptionKey(model);

        if (hasI18n) {
            MethodSpec.Builder methodSpec = MethodSpec.methodBuilder("getCommandInfo")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(ClassName.get(List.class), commandInfoClass))
                    .addStatement("$T<$T> list = new $T<>()", List.class, commandInfoClass, ArrayList.class);
            generateCommandInfoStatements(methodSpec, model, new ArrayList<>(), commandInfoClass);
            methodSpec.addStatement("return list");
            typeSpec.addMethod(methodSpec.build());
        } else {
            typeSpec.addField(FieldSpec.builder(
                    ParameterizedTypeName.get(ClassName.get(List.class), commandInfoClass),
                    "COMMAND_INFO",
                    Modifier.PRIVATE, Modifier.STATIC, Modifier.FINAL).build());

            CodeBlock.Builder staticBlock = CodeBlock.builder();
            staticBlock.addStatement("$T<$T> list = new $T<>()", List.class, commandInfoClass, ArrayList.class);
            generateCommandInfoStatements(null, model, new ArrayList<>(), commandInfoClass, staticBlock);
            staticBlock.addStatement("COMMAND_INFO = $T.unmodifiableList(list)", Collections.class);
            typeSpec.addStaticBlock(staticBlock.build());

            typeSpec.addMethod(MethodSpec.methodBuilder("getCommandInfo")
                    .addModifiers(Modifier.PUBLIC)
                    .returns(ParameterizedTypeName.get(ClassName.get(List.class), commandInfoClass))
                    .addStatement("return COMMAND_INFO")
                    .build());
        }
    }

    private void generateCommandInfoStatements(MethodSpec.Builder methodSpec, CommandModel model, List<String> parentPath, ClassName commandInfoClass) {
        generateCommandInfoStatements0(model, parentPath, commandInfoClass, methodSpec::addStatement);
    }

    private void generateCommandInfoStatements(MethodSpec.Builder ignored, CommandModel model, List<String> parentPath, ClassName commandInfoClass, CodeBlock.Builder staticBlock) {
        generateCommandInfoStatements0(model, parentPath, commandInfoClass, staticBlock::addStatement);
    }

    private void generateCommandInfoStatements0(CommandModel model, List<String> parentPath, ClassName commandInfoClass, StatementAdder adder) {
        List<String> currentPath = new ArrayList<>(parentPath);
        currentPath.add(model.getCommandName());

        if (model.getDefaultMethod() != null) {
            String usage = getUsage(model.getDefaultMethod(), model);
            String desc = model.getDescription();
            addDescription(adder, commandInfoClass, currentPath, usage, desc);
        }

        for (MethodModel sub : model.getSubcommands()) {
            List<String> subPath = new ArrayList<>(currentPath);
            subPath.add(sub.getSubcommandName());
            String usage = getUsage(sub, model);
            String desc = sub.getDescription();
            addDescription(adder, commandInfoClass, subPath, usage, desc);
        }

        for (CommandModel child : model.getNestedSubcommands()) {
            generateCommandInfoStatements0(child, currentPath, commandInfoClass, adder);
        }
    }

    private void addDescription(StatementAdder adder, ClassName commandInfoClass, List<String> path, String usage, String desc) {
        if (isI18nKey(desc)) {
            adder.add("list.add(new $T($L, $S, manager.formatMessage($S, $S)))",
                    commandInfoClass, buildPathExpression(path), usage, i18nKey(desc), desc);
        } else {
            adder.add("list.add(new $T($L, $S, $S))",
                    commandInfoClass, buildPathExpression(path), usage, desc);
        }
    }

    private CodeBlock buildPathExpression(List<String> path) {
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

    public ExecutableElement findLocalResolver(CommandModel classModel, ParameterModel p, CommandModel rootModel) {
        return resolverLookup.findLocalResolver(classModel, p);
    }

    protected ExecutableElement findSuggestMethod(TypeElement typeElement, String name) {
        return resolverLookup.findSuggestMethod(typeElement, name);
    }

    protected boolean isField(TypeElement typeElement, String name) {
        return resolverLookup.isField(typeElement, name);
    }

    public CommandModel findModelForClass(CommandModel current, TypeElement targetClass) {
        return resolverLookup.findModelForClass(current, targetClass);
    }

    public int getBuiltInWidth(TypeName typeName) {
        return typeSupport.getWidth(typeName);
    }

    public boolean isPlatformBuiltInType(TypeName typeName) {
        TypeSupport.Entry e = typeSupport.get(typeName);
        return e != null && (e.platformResolution != null || e.platformMultiResolution != null);
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

    public int getLocalResolverMinWidth(ExecutableElement resolverMethod, MethodModel commandMethod) {
        int minWidth = 0;
        List<? extends VariableElement> params = resolverMethod.getParameters();
        int startIndex = firstParamIsSender(resolverMethod, commandMethod) ? 1 : 0;
        for (int i = startIndex; i < params.size(); i++) {
            if (params.get(i).getAnnotation(Default.class) == null) {
                minWidth++;
            }
        }
        return minWidth;
    }

    public int getLocalResolverMaxWidth(ExecutableElement resolverMethod, MethodModel commandMethod) {
        List<? extends VariableElement> params = resolverMethod.getParameters();
        int startIndex = firstParamIsSender(resolverMethod, commandMethod) ? 1 : 0;
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

    private Set<TypeName> getSenderTypesToCast(CommandModel model) {
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
        if (!isSenderBaseType(typeName) && senderParam.getElement().getAnnotation(Resolve.class) == null) {
            types.add(typeName);
        }
        for (ParameterModel p : method.getParameters()) {
            if (p == method.getSenderParameter()) continue;
            Resolve resolveAnn = p.getElement().getAnnotation(Resolve.class);
            if (resolveAnn != null) {
                ExecutableElement resolver = resolverLookup.findMethod((TypeElement) method.getElement().getEnclosingElement(), resolveAnn.value());
                if (resolver != null && !resolver.getParameters().isEmpty()) {
                    TypeName firstParamType = TypeName.get(resolver.getParameters().get(0).asType());
                    if (isSenderType(firstParamType) && !isSenderBaseType(firstParamType)) {
                        types.add(firstParamType);
                    }
                }
            }
            Suggest suggestAnn = p.getElement().getAnnotation(Suggest.class);
            if (suggestAnn != null) {
                ExecutableElement suggestMethod = findSuggestMethod((TypeElement) method.getElement().getEnclosingElement(), suggestAnn.value());
                if (suggestMethod != null && !suggestMethod.getParameters().isEmpty()) {
                    TypeName firstParamType = TypeName.get(suggestMethod.getParameters().get(0).asType());
                    if (isSenderType(firstParamType) && !isSenderBaseType(firstParamType)) {
                        types.add(firstParamType);
                    }
                }
            }
        }
    }

    private boolean hasBooleanParameter(CommandModel model) {
        if (model.getDefaultMethod() != null && hasBooleanParameter(model.getDefaultMethod())) return true;
        for (MethodModel sub : model.getSubcommands()) {
            if (hasBooleanParameter(sub)) return true;
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            if (hasBooleanParameter(child)) return true;
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

    private List<String> collectLoweredNames(CommandModel child) {
        List<String> names = new ArrayList<>();
        names.add(child.getCommandName().toLowerCase());
        for (String alias : child.getAliases()) {
            names.add(alias.toLowerCase());
        }
        return names;
    }

    private List<String> collectLoweredNames(MethodModel sub) {
        List<String> names = new ArrayList<>();
        names.add(sub.getSubcommandName().toLowerCase());
        for (String alias : sub.getAliases()) {
            names.add(alias.toLowerCase());
        }
        return names;
    }

    private boolean isStringArray(TypeMirror type) {
        if (type.getKind() != TypeKind.ARRAY) return false;
        ArrayType arrayType = (ArrayType) type;
        TypeMirror componentType = arrayType.getComponentType();
        if (componentType.getKind() != TypeKind.DECLARED) return false;
        TypeElement componentElement = (TypeElement) ((DeclaredType) componentType).asElement();
        return componentElement.getQualifiedName().toString().equals("java.lang.String");
    }

    // ── SPI Invocation Helpers ──

    private void runSPIAnnotationHandlers(MethodSpec.Builder methodSpec, MethodModel method, String instanceVar, String senderVarName, ParameterModel senderParam) {
        for (MethodAnnotationHandler<?> handler : methodHandlers) {
            Annotation ann = method.getElement().getAnnotation(handler.annotationType());
            if (ann != null) {
                invokeMethodHandler(handler, ann, method, instanceVar, senderVarName, methodSpec);
            }
        }
        runParameterAnnotationHandlers(senderParam.getElement(), senderVarName, instanceVar, "sender", methodSpec);
    }

    public void runParameterAnnotationHandlers(VariableElement param, String varName, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec) {
        for (ParameterAnnotationHandler<?> handler : parameterHandlers) {
            Annotation ann = param.getAnnotation(handler.annotationType());
            if (ann != null) {
                invokeParameterHandler(handler, ann, new ParameterModel(
                        param.getSimpleName().toString(),
                        param.asType(),
                        param.getAnnotation(Greedy.class) != null,
                        param.getAnnotation(Default.class) != null,
                        param.getAnnotation(Default.class) != null && !param.getAnnotation(Default.class).value().isEmpty() ? param.getAnnotation(Default.class).value() : null,
                        param.getAnnotation(Suggest.class) != null ? param.getAnnotation(Suggest.class).value() : null,
                        param
                ), varName, instanceExpr, senderVar, methodSpec);
            }
        }
    }

    @SuppressWarnings("unchecked")
    private <A extends Annotation> void invokeParameterHandler(ParameterAnnotationHandler<A> handler, Annotation annotation, ParameterModel parameter, String varName, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec) {
        handler.handle((A) annotation, parameter, varName, instanceExpr, senderVar, methodSpec);
    }

    @SuppressWarnings("unchecked")
    private <A extends Annotation> void invokeMethodHandler(MethodAnnotationHandler<A> handler, Annotation annotation, MethodModel method, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec) {
        handler.handle((A) annotation, method, instanceExpr, senderVar, methodSpec);
    }

    @FunctionalInterface
    private interface StatementAdder {
        void add(String fmt, Object... args);
    }
}

