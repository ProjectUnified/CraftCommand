package io.github.projectunified.craftcommand.processor.paper;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import com.palantir.javapoet.WildcardTypeName;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.TypeNames;
import io.github.projectunified.craftcommand.processor.generator.BaseCommandGenerator;
import io.github.projectunified.craftcommand.processor.generator.Naming;
import io.github.projectunified.craftcommand.processor.generator.TypeSupport;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.lang.model.util.Elements;
import javax.lang.model.util.Types;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Deque;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Function;

/**
 * Generates {@code *$PaperCommand} wrappers with a native Brigadier tree.
 */
public final class PaperCommandGenerator extends BaseCommandGenerator {
    final ClassName commandSourceStackClass = ClassName.get("io.papermc.paper.command.brigadier", "CommandSourceStack");
    final ClassName literalCommandNodeClass = ClassName.get("com.mojang.brigadier.tree", "LiteralCommandNode");
    final ClassName commandsClass = ClassName.get("io.papermc.paper.command.brigadier", "Commands");
    final ClassName commandClass = ClassName.get("com.mojang.brigadier", "Command");
    final ClassName requiredArgumentBuilderClass = ClassName.get("com.mojang.brigadier.builder", "RequiredArgumentBuilder");
    final ClassName literalArgumentBuilderClass = ClassName.get("com.mojang.brigadier.builder", "LiteralArgumentBuilder");
    final ClassName argumentTypesClass = ClassName.get("io.papermc.paper.command.brigadier.argument", "ArgumentTypes");
    final ClassName paperSuggestionsClass = ClassName.get("io.github.projectunified.craftcommand.paper", "PaperSuggestions");

    private final ProcessingEnvironment processingEnvironment;
    private final Map<String, CodeBlock> brigadierArgTypes = new HashMap<>();
    private final Map<String, Function<String, CodeBlock>> brigadierRetrievals = new HashMap<>();

    public PaperCommandGenerator(ProcessingEnvironment processingEnvironment) {
        super(processingEnvironment);
        this.processingEnvironment = processingEnvironment;

        senderTypeRegistry().registerSenderBaseType("io.papermc.paper.command.brigadier.CommandSourceStack");
        senderTypeRegistry().registerSenderType("org.bukkit.entity.Player");
        senderTypeRegistry().registerSenderType("org.bukkit.command.ConsoleCommandSender");
        senderTypeRegistry().registerSenderType("org.bukkit.command.BlockCommandSender");
        senderTypeRegistry().registerSenderType("org.bukkit.command.CommandSender");

        ClassName locationClass = ClassName.get("org.bukkit", "Location");
        typeSupport().register(TypeSupport.Entry.builder(locationClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null")).build());

        ClassName strArgClass = ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType");
        ClassName intArgClass = ClassName.get("com.mojang.brigadier.arguments", "IntegerArgumentType");
        ClassName longArgClass = ClassName.get("com.mojang.brigadier.arguments", "LongArgumentType");
        ClassName dblArgClass = ClassName.get("com.mojang.brigadier.arguments", "DoubleArgumentType");
        ClassName fltArgClass = ClassName.get("com.mojang.brigadier.arguments", "FloatArgumentType");
        ClassName boolArgClass = ClassName.get("com.mojang.brigadier.arguments", "BoolArgumentType");
        ClassName finePositionClass = ClassName.get("io.papermc.paper.command.brigadier.argument.resolvers", "FinePositionResolver");

        brigadierArgTypes.put("java.lang.String", CodeBlock.of("$T.string()", strArgClass));
        brigadierRetrievals.put("java.lang.String", a -> CodeBlock.of("$T.getString(ctx, $S)", strArgClass, a));

        registerBrigadierType("int", "java.lang.Integer", intArgClass, "integer", "getInteger");
        registerBrigadierType("long", "java.lang.Long", longArgClass, "longArg", "getLong");
        registerBrigadierType("double", "java.lang.Double", dblArgClass, "doubleArg", "getDouble");
        registerBrigadierType("float", "java.lang.Float", fltArgClass, "floatArg", "getFloat");
        registerBrigadierType("boolean", "java.lang.Boolean", boolArgClass, "bool", "getBool");

        registerPaperArgumentTypes();

        brigadierArgTypes.put("org.bukkit.Location", CodeBlock.of("$T.finePosition(true)", argumentTypesClass));
        brigadierRetrievals.put("org.bukkit.Location", a -> CodeBlock.of("ctx.getArgument($S, $T.class).resolve(ctx.getSource()).toLocation(ctx.getSource().getLocation().getWorld())", a, finePositionClass));
        registerPaperType(ClassName.get("org.bukkit.util", "Vector"), CodeBlock.of("$T.finePosition(true)", argumentTypesClass),
                a -> CodeBlock.of("ctx.getArgument($S, $T.class).resolve(ctx.getSource()).toVector()", a, finePositionClass));
    }

    /**
     * Registers every no-argument factory on Paper's {@code ArgumentTypes}. The resolved value type comes from the
     * factory's generic return type, so new paper-api arguments are picked up without touching this generator.
     */
    private void registerPaperArgumentTypes() {
        Elements elements = processingEnvironment.getElementUtils();
        Types types = processingEnvironment.getTypeUtils();
        TypeElement argumentTypes = elements.getTypeElement("io.papermc.paper.command.brigadier.argument.ArgumentTypes");
        if (argumentTypes == null) return;

        for (Element enclosed : argumentTypes.getEnclosedElements()) {
            if (!(enclosed instanceof ExecutableElement method)) continue;
            if (!method.getParameters().isEmpty() || !method.getModifiers().contains(Modifier.STATIC)) continue;
            if (!(method.getReturnType() instanceof DeclaredType returnType)) continue;

            TypeElement returnElement = (TypeElement) returnType.asElement();
            if (!returnElement.getQualifiedName().contentEquals("com.mojang.brigadier.arguments.ArgumentType")
                    || returnType.getTypeArguments().size() != 1) {
                continue;
            }

            TypeMirror valueType = returnType.getTypeArguments().get(0);
            CodeBlock argumentType = CodeBlock.of("$T.$L()", argumentTypesClass, method.getSimpleName());
            TypeMirror resolved = resolvedType(valueType, types);
            if (resolved instanceof DeclaredType declaredResolved
                    && declaredResolved.asElement().getSimpleName().contentEquals("List")
                    && declaredResolved.getTypeArguments().size() == 1) {
                TypeMirror elementType = declaredResolved.getTypeArguments().get(0);
                registerPaperTypeIfAbsent(TypeName.get(resolved), argumentType,
                        a -> CodeBlock.of("ctx.getArgument($S, $T.class).resolve(ctx.getSource())", a, TypeName.get(valueType)));
                registerPaperTypeIfAbsent(TypeName.get(elementType), argumentType,
                        a -> CodeBlock.of("ctx.getArgument($S, $T.class).resolve(ctx.getSource()).iterator().next()", a, TypeName.get(valueType)));
            } else if (resolved != null) {
                TypeName target = TypeName.get(resolved);
                registerPaperTypeIfAbsent(target, argumentType,
                        a -> CodeBlock.of("ctx.getArgument($S, $T.class).resolve(ctx.getSource())", a, TypeName.get(valueType)));
            } else {
                TypeName target = TypeName.get(valueType);
                registerPaperTypeIfAbsent(target, argumentType,
                        a -> CodeBlock.of("ctx.getArgument($S, $T.class)", a, target));
            }
        }
    }

    /**
     * If the argument type is a resolver, returns the type it resolves to.
     */
    private TypeMirror resolvedType(TypeMirror valueType, Types types) {
        if (!(valueType instanceof DeclaredType declaredValueType)) return null;
        Deque<TypeMirror> queue = new ArrayDeque<>(types.directSupertypes(declaredValueType));
        while (!queue.isEmpty()) {
            TypeMirror superType = queue.removeFirst();
            if (!(superType instanceof DeclaredType declared)) continue;
            TypeElement element = (TypeElement) declared.asElement();
            if (element.getQualifiedName().contentEquals("io.papermc.paper.command.brigadier.argument.resolvers.ArgumentResolver")
                    && declared.getTypeArguments().size() == 1) {
                return declared.getTypeArguments().get(0);
            }
            queue.addAll(types.directSupertypes(superType));
        }
        return null;
    }

    private void registerPaperTypeIfAbsent(TypeName type, CodeBlock argumentType, Function<String, CodeBlock> retrieval) {
        typeSupport().register(TypeSupport.Entry.builder(type, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null")).build());
        brigadierArgTypes.putIfAbsent(type.toString(), argumentType);
        brigadierRetrievals.putIfAbsent(type.toString(), retrieval);
    }

    private void registerPaperType(TypeName type, CodeBlock argumentType, Function<String, CodeBlock> retrieval) {
        typeSupport().register(TypeSupport.Entry.builder(type, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null")).build());
        brigadierArgTypes.put(type.toString(), argumentType);
        brigadierRetrievals.put(type.toString(), retrieval);
    }

    private static Permission findPermissionUp(Element element) {
        Element current = element;
        while (current != null) {
            Permission permission = current.getAnnotation(Permission.class);
            if (permission != null) return permission;
            current = current.getEnclosingElement();
        }
        return null;
    }

    private void registerBrigadierType(String primitive, String wrapper, ClassName argClass, String argMethod, String retrievalMethod) {
        CodeBlock argType = CodeBlock.of("$T.$L()", argClass, argMethod);
        Function<String, CodeBlock> retrieval = a -> CodeBlock.of("$T.$L(ctx, $S)", argClass, retrievalMethod, a);
        brigadierArgTypes.put(primitive, argType);
        brigadierArgTypes.put(wrapper, argType);
        brigadierRetrievals.put(primitive, retrieval);
        brigadierRetrievals.put(wrapper, retrieval);
    }

    @Override
    protected String getWrapperClassSuffix() {
        return "$PaperCommand";
    }

    @Override
    protected void configureClass(TypeSpec.Builder typeSpec, CommandModel model) {
        typeSpec.addSuperinterface(ClassName.get("io.github.projectunified.craftcommand.paper", "PaperCommand"));
    }

    @Override
    public ClassName getSenderTypeName() {
        return commandSourceStackClass;
    }

    @Override
    protected TypeName getManagerType() {
        return ParameterizedTypeName.get(ClassName.get("io.github.projectunified.craftcommand", "CommandManager"),
                commandSourceStackClass);
    }

    @Override
    protected CodeBlock getSenderExpression(String senderVar) {
        return CodeBlock.of("$L.getSender()", senderVar);
    }

    @Override
    protected void generateEntryMethods(TypeSpec.Builder typeSpec, CommandModel model) {
        typeSpec.addMethod(MethodSpec.methodBuilder("getDescription")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(String.class)
                .addStatement("return $S", model.getDescription()).build());
        typeSpec.addMethod(MethodSpec.methodBuilder("getAliases")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(Collection.class, String.class))
                .addStatement("return $L", buildAliasesExpression(model)).build());

        MethodSpec.Builder getCommandNodeSpec = MethodSpec.methodBuilder("getCommandNode")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(literalCommandNodeClass, commandSourceStackClass));
        getCommandNodeSpec.addStatement("$T builder = $T.literal($S)",
                ParameterizedTypeName.get(literalArgumentBuilderClass, commandSourceStackClass),
                commandsClass, model.getCommandName());
        buildBrigadierTree(getCommandNodeSpec, model, "builder", "instance", model);
        getCommandNodeSpec.addStatement("return builder.build()");
        typeSpec.addMethod(getCommandNodeSpec.build());
    }

    @Override
    protected void generateHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        buildSenderCastHelpers(typeSpec, model);
        buildTypeSuggestionHelpers(typeSpec, model);
        if (hasDefaultPermission(model)) typeSpec.addMethod(permissionCheck());
    }

    private static boolean hasDefaultPermission(CommandModel model) {
        return anyMethod(model, element -> {
            Permission permission = findPermissionUp(element);
            return permission != null && permission.message().isEmpty();
        });
    }

    private MethodSpec permissionCheck() {
        return MethodSpec.methodBuilder("checkPermission")
                .addModifiers(Modifier.PRIVATE)
                .returns(boolean.class)
                .addParameter(commandSourceStackClass, "sender")
                .addParameter(String.class, "permission")
                .addStatement("if (sender.getSender().hasPermission(permission)) return true")
                .addStatement("manager.sendMessage(sender, $S, $S, permission)",
                        PERMISSION_KEY, DEFAULT_PERMISSION_MESSAGE)
                .addStatement("return false")
                .build();
    }

    @Override
    protected void onBeforeExecute(MethodSpec.Builder methodSpec, Element element, String returnStatement) {
        Permission permission = findPermissionUp(element);
        if (permission == null) return;
        String message = permission.message();
        if (message.isEmpty()) {
            methodSpec.addStatement("if (!checkPermission(sender, $S)) return $T.SINGLE_SUCCESS",
                    permission.value(), commandClass);
            return;
        }
        methodSpec.beginControlFlow("if (!sender.getSender().hasPermission($S))", permission.value());
        if (isI18nKey(message)) {
            methodSpec.addStatement("manager.sendMessage(sender, $S, $S, $S)",
                    i18nKey(message), message, permission.value());
        } else {
            methodSpec.addStatement("manager.sendMessage(sender, $S, $S)", message, message);
        }
        methodSpec.addStatement("return $T.SINGLE_SUCCESS", commandClass);
        methodSpec.endControlFlow();
    }

    // ── Brigadier tree generation ──

    private void buildBrigadierTree(MethodSpec.Builder spec, CommandModel model, String builderVar, String instanceExpr, CommandModel rootModel) {
        for (CommandModel child : model.getNestedSubcommands()) {
            String childInstanceVar = "this." + getSubcommandFieldName(child);
            String childBuilderVar = "subBuilder_" + Naming.sanitizeIdentifier(child.getCommandName());
            spec.addStatement("$T $L = $T.literal($S)",
                    ParameterizedTypeName.get(literalArgumentBuilderClass, commandSourceStackClass),
                    childBuilderVar, commandsClass, child.getCommandName());
            buildBrigadierTree(spec, child, childBuilderVar, childInstanceVar, rootModel);
            spec.addStatement("$L.then($L)", builderVar, childBuilderVar);
        }
        for (MethodModel sub : model.getSubcommands()) {
            String subBuilderVar = "subBuilder_" + Naming.sanitizeIdentifier(sub.subcommandName());
            spec.addStatement("$T $L = $T.literal($S)",
                    ParameterizedTypeName.get(literalArgumentBuilderClass, commandSourceStackClass),
                    subBuilderVar, commandsClass, sub.subcommandName());
            buildMethodParametersTree(spec, subBuilderVar, model, sub, instanceExpr, rootModel);
            spec.addStatement("$L.then($L)", builderVar, subBuilderVar);
        }
        if (model.getDefaultMethod() != null) {
            buildMethodParametersTree(spec, builderVar, model, model.getDefaultMethod(), instanceExpr, rootModel);
        }
    }

    private void buildMethodParametersTree(MethodSpec.Builder spec, String parentBuilderVar, CommandModel classModel, MethodModel method, String instanceExpr, CommandModel rootModel) {
        List<NodeInfo> nodes = new ArrayList<>();
        for (ParameterModel p : method.parameters()) {
            if (p == method.senderParameter()) continue;
            MethodModel resolverModel = p.resolverMethod();
            if (resolverModel != null) {
                ExecutableElement localResolver = resolverModel.element();
                List<ParameterModel> resolverParams = resolverModel.parameters();
                int resolverStartIndex = firstParamIsSender(localResolver, method) ? 1 : 0;
                int resolverWidth = resolverParams.size() - resolverStartIndex;
                for (int i = 0; i < resolverWidth; i++) {
                    ParameterModel rp = resolverParams.get(resolverStartIndex + i);
                    TypeName rpTypeName = TypeName.get(rp.type());
                    if (isSenderParam(rpTypeName, method)) {
                        continue;
                    }
                    String rpName = rp.name();
                    CodeBlock typeBlock = getArgumentTypeExpressionFromTypeName(rpTypeName, rp.greedy());
                    String suggestProvider = rp.suggestProvider() != null ? rp.suggestProvider() : p.suggestProvider();
                    MethodModel suggestMethod = rp.suggestProvider() != null ? rp.suggestMethod() : p.suggestMethod();
                    boolean suggestField = rp.suggestProvider() != null ? rp.suggestField() : p.suggestField();
                    nodes.add(new NodeInfo(rpName, typeBlock, p, i, i == resolverWidth - 1, rp.optional(),
                            suggestProvider, rpTypeName, suggestMethod, suggestField, rp.enumConstants() != null));
                }
            } else {
                int width = getParameterWidth(p, method);
                CodeBlock typeBlock = getArgumentTypeExpression(p);
                if (width <= 1) {
                    nodes.add(new NodeInfo(p.name(), typeBlock, p, 0, true, false,
                            p.suggestProvider(), null, p.suggestMethod(), p.suggestField(),
                            p.enumConstants() != null));
                } else {
                    for (int i = 0; i < width; i++) {
                        nodes.add(new NodeInfo(p.name() + "_" + i,
                                CodeBlock.of("$T.string()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType")),
                                p, i, i == width - 1, false,
                                p.suggestProvider(), null, p.suggestMethod(), p.suggestField(),
                                p.enumConstants() != null));
                    }
                }
            }
        }
        buildNodeChainRecursive(spec, parentBuilderVar, classModel, method, instanceExpr, rootModel, nodes, 0);
    }

    private int getParameterWidth(ParameterModel param, MethodModel method) {
        MethodModel resolverModel = param.resolverMethod();
        if (resolverModel != null) return getLocalResolverMaxWidth(resolverModel, method);
        return getBuiltInWidth(TypeName.get(param.type()));
    }

    private void buildNodeChainRecursive(MethodSpec.Builder spec, String currentBuilderVar, CommandModel classModel, MethodModel method, String instanceExpr, CommandModel rootModel, List<NodeInfo> nodes, int index) {
        boolean canExecuteHere = false;
        if (index == nodes.size()) {
            canExecuteHere = true;
        } else if (index == 0) {
            if (nodes.get(0).parameter().optional() || nodes.get(0).isResolverParamOptional()) {
                canExecuteHere = true;
            }
        } else if (nodes.get(index - 1).isLastForParameter()) {
            if (nodes.get(index).parameter().optional()) canExecuteHere = true;
        } else if (nodes.get(index).isResolverParamOptional()) {
            canExecuteHere = true;
        }

        if (canExecuteHere) {
            spec.beginControlFlow("$L.executes(ctx ->", currentBuilderVar);
            generateExecutionBlock(spec, classModel, method, instanceExpr, rootModel, nodes, index);
            spec.addCode("$<});\n");
        }

        if (index < nodes.size()) {
            NodeInfo node = nodes.get(index);
            String nextBuilderVar = "argBuilder_" + Naming.sanitizeIdentifier(method.methodName()) + "_" + Naming.sanitizeIdentifier(node.nodeName());
            spec.addStatement("$T $L = $T.argument($S, $L)",
                    ParameterizedTypeName.get(requiredArgumentBuilderClass, commandSourceStackClass, WildcardTypeName.subtypeOf(TypeName.get(Object.class))),
                    nextBuilderVar, commandsClass, node.nodeName(), node.typeExpression());

            ParameterModel p = node.parameter();
            TypeName parameterType = TypeName.get(p.type());
            boolean needsSuggestions = node.suggestProvider() != null
                    || node.enumParam() || TypeSupport.isBooleanType(parameterType);
            if (!needsSuggestions && node.resolverParamType() != null) {
                TypeName rpType = node.resolverParamType();
                if (TypeSupport.isBooleanType(rpType)) {
                    needsSuggestions = true;
                } else if (typeSupport().hasPlatformSuggestions(rpType)) {
                    needsSuggestions = true;
                }
            }

            if (needsSuggestions && (node.isLastForParameter() || node.resolverParamType() != null)) {
                if (node.suggestProvider() != null) {
                    if (node.suggestField()) {
                        spec.addStatement("$L.suggests((ctx, sb) -> $T.suggestMatching(ctx, sb, args -> $T.filterSuggestions($L.$L, args[args.length - 1])))",
                                nextBuilderVar, paperSuggestionsClass, ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, node.suggestProvider());
                    } else {
                        CodeBlock suggestSenderExpr = CodeBlock.of("ctx.getSource()");
                        boolean needsCast = false;
                        MethodModel suggestMethod = node.suggestMethod();
                        if (suggestMethod != null && !suggestMethod.parameters().isEmpty()) {
                            ParameterModel firstParam = suggestMethod.parameters().get(0);
                            TypeName firstParamType = TypeName.get(firstParam.type());
                            if (!firstParamType.equals(getSenderTypeName())) {
                                String firstParamResolve = firstParam.resolveName();
                                if (firstParamResolve != null) {
                                    if (!firstParamResolve.isEmpty()) {
                                        MethodModel resolver = firstParam.resolverMethod();
                                        if (resolver != null) {
                                            String resolverInstanceExpr = getInstanceVarExpression(classModel, rootModel);
                                            suggestSenderExpr = BaseCommandGenerator.call(resolverInstanceExpr,
                                                    resolver.element().getSimpleName().toString(), CodeBlock.of("ctx.getSource()"));
                                        }
                                    } else {
                                        suggestSenderExpr = CodeBlock.of("($T) manager.resolveSender($T.class, ctx.getSource())", firstParamType, firstParamType);
                                    }
                                } else if (isSenderType(firstParamType)) {
                                    suggestSenderExpr = CodeBlock.of("$L(ctx.getSource())", "as" + Naming.simpleName(firstParamType));
                                    needsCast = true;
                                }
                            }
                        }
                        CodeBlock callExpr = suggestMethod != null
                                ? getSuggestCallExpr(suggestMethod, method, instanceExpr, node.suggestProvider(), suggestSenderExpr)
                                : BaseCommandGenerator.call(instanceExpr, node.suggestProvider());
                        if (needsCast) {
                            ClassName suggestionsClass = ClassName.get("com.mojang.brigadier.suggestion", "Suggestions");
                            spec.beginControlFlow("$L.suggests((ctx, sb) ->", nextBuilderVar)
                                    .beginControlFlow("try")
                                    .addStatement("return $T.suggestMatching(ctx, sb, args -> $L)", paperSuggestionsClass, callExpr)
                                    .nextControlFlow("catch ($T e)", CommandException.class)
                                    .addStatement("return $T.empty()", suggestionsClass)
                                    .endControlFlow()
                                    .addCode("$<});\n");
                        } else {
                            spec.addStatement("$L.suggests((ctx, sb) -> $T.suggestMatching(ctx, sb, args -> $L))",
                                    nextBuilderVar, paperSuggestionsClass, callExpr);
                        }
                    }
                } else {
                    String resolveName = p.resolveName();
                    if (resolveName != null && !resolveName.isEmpty()) {
                        MethodModel resolverModel = p.resolverMethod();
                        int resolverStartIndex = resolverModel == null ? 0 : firstParamIsSender(resolverModel.element(), method) ? 1 : 0;
                        ParameterModel resolverParam = resolverModel == null ? null
                                : resolverModel.parameters().get(resolverStartIndex + node.resolverArgIndex());
                        String helperName = getResolverParamSuggestionMethodName(classModel, resolverParam);
                        spec.addStatement("$L.suggests((ctx, sb) -> $T.suggestMatching(ctx, sb, args -> $L(ctx.getSource(), args)))",
                                nextBuilderVar, paperSuggestionsClass, helperName);
                    } else {
                        int paramIndex = method.parameters().indexOf(p);
                        String helperName = getParameterSuggestionMethodName(classModel, method, paramIndex);
                        spec.addStatement("$L.suggests((ctx, sb) -> $T.suggestMatching(ctx, sb, args -> $L(ctx.getSource(), args)))",
                                nextBuilderVar, paperSuggestionsClass, helperName);
                    }
                }
            }
            buildNodeChainRecursive(spec, nextBuilderVar, classModel, method, instanceExpr, rootModel, nodes, index + 1);
            spec.addStatement("$L.then($L)", currentBuilderVar, nextBuilderVar);
        }
    }

    private void generateExecutionBlock(MethodSpec.Builder spec, CommandModel classModel, MethodModel method, String instanceExpr, CommandModel rootModel, List<NodeInfo> nodes, int parsedNodeCount) {
        spec.addStatement("$T sender = ctx.getSource()", commandSourceStackClass);
        spec.beginControlFlow("try");
        onBeforeExecute(spec, method.element(), "return");
        new PaperExecutionSource(this, nodes, parsedNodeCount).generateExecution(spec, classModel, method, instanceExpr, rootModel);
        spec.nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("manager.handleException(sender, e)")
                .endControlFlow();
        spec.addStatement("return $T.SINGLE_SUCCESS", commandClass);
    }

    private CodeBlock getArgumentTypeExpression(ParameterModel param) {
        return getArgumentTypeExpressionFromTypeName(TypeName.get(param.type()), param.greedy());
    }

    private CodeBlock getArgumentTypeExpressionFromTypeName(TypeName typeName, boolean isGreedy) {
        if (isGreedy) return CodeBlock.of("$T.greedyString()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"));
        CodeBlock argType = brigadierArgTypes.get(typeName.toString());
        return argType != null
                ? argType
                : CodeBlock.of("$T.string()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"));
    }

    CodeBlock getArgumentRetrievalExpression(TypeName typeName, String argName) {
        Function<String, CodeBlock> provider = brigadierRetrievals.get(typeName.toString());
        return provider != null ? provider.apply(argName) : CodeBlock.of("ctx.getArgument($S, $T.class)", argName, typeName);
    }

    private CodeBlock getSuggestCallExpr(MethodModel suggestMethod, MethodModel method, String instanceExpr, String suggestProvider, CodeBlock suggestSenderExpr) {
        int argCount = suggestMethod.parameters().size();
        if (argCount == 0) {
            return BaseCommandGenerator.call(instanceExpr, suggestProvider);
        }
        CodeBlock current = CodeBlock.of("new String[]{args[args.length - 1]}");
        if (argCount == 1) {
            TypeMirror firstParamType = suggestMethod.parameters().get(0).type();
            return TypeNames.isStringArray(firstParamType)
                    ? BaseCommandGenerator.call(instanceExpr, suggestProvider, current)
                    : BaseCommandGenerator.call(instanceExpr, suggestProvider, suggestSenderExpr);
        }
        if (argCount == 2) {
            TypeMirror firstParamType = suggestMethod.parameters().get(0).type();
            return TypeNames.isStringArray(firstParamType)
                    ? BaseCommandGenerator.call(instanceExpr, suggestProvider, current, CodeBlock.of("args"))
                    : BaseCommandGenerator.call(instanceExpr, suggestProvider, suggestSenderExpr, current);
        }
        if (argCount == 3) {
            return BaseCommandGenerator.call(instanceExpr, suggestProvider, suggestSenderExpr, current, CodeBlock.of("args"));
        }
        return BaseCommandGenerator.call(instanceExpr, suggestProvider);
    }

    record NodeInfo(String nodeName, CodeBlock typeExpression, ParameterModel parameter, int resolverArgIndex,
                    boolean isLastForParameter, boolean isResolverParamOptional, String suggestProvider,
                    TypeName resolverParamType, MethodModel suggestMethod, boolean suggestField, boolean enumParam) {
    }
}
