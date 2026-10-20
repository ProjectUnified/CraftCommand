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
import javax.lang.model.type.TypeMirror;
import java.util.ArrayList;
import java.util.Collection;
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
    final ClassName errorColorClass = ClassName.get("net.kyori.adventure.text.format", "NamedTextColor");
    final ClassName componentClass = ClassName.get("net.kyori.adventure.text", "Component");
    final ClassName paperSuggestionsClass = ClassName.get("io.github.projectunified.craftcommand.paper", "PaperSuggestions");

    private final Map<String, CodeBlock> brigadierArgTypes = new HashMap<>();
    private final Map<String, Function<String, CodeBlock>> brigadierRetrievals = new HashMap<>();

    public PaperCommandGenerator(ProcessingEnvironment processingEnvironment) {
        super(processingEnvironment);

        senderTypeRegistry().registerSenderBaseType("io.papermc.paper.command.brigadier.CommandSourceStack");
        senderTypeRegistry().registerSenderType("org.bukkit.entity.Player");
        senderTypeRegistry().registerSenderType("org.bukkit.command.ConsoleCommandSender");
        senderTypeRegistry().registerSenderType("org.bukkit.command.BlockCommandSender");
        senderTypeRegistry().registerSenderType("org.bukkit.command.CommandSender");

        ClassName playerClass = ClassName.get("org.bukkit.entity", "Player");
        ClassName worldClass = ClassName.get("org.bukkit", "World");
        ClassName locationClass = ClassName.get("org.bukkit", "Location");
        typeSupport().register(TypeSupport.Entry.builder(playerClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null")).build());
        typeSupport().register(TypeSupport.Entry.builder(worldClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null")).build());
        typeSupport().register(TypeSupport.Entry.builder(locationClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null")).build());

        ClassName strArgClass = ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType");
        ClassName intArgClass = ClassName.get("com.mojang.brigadier.arguments", "IntegerArgumentType");
        ClassName longArgClass = ClassName.get("com.mojang.brigadier.arguments", "LongArgumentType");
        ClassName dblArgClass = ClassName.get("com.mojang.brigadier.arguments", "DoubleArgumentType");
        ClassName fltArgClass = ClassName.get("com.mojang.brigadier.arguments", "FloatArgumentType");
        ClassName boolArgClass = ClassName.get("com.mojang.brigadier.arguments", "BoolArgumentType");
        ClassName playerResolverClass = ClassName.get("io.papermc.paper.command.brigadier.argument.resolvers.selector", "PlayerSelectorArgumentResolver");
        ClassName finePositionClass = ClassName.get("io.papermc.paper.command.brigadier.argument.resolvers", "FinePositionResolver");

        brigadierArgTypes.put("java.lang.String", CodeBlock.of("$T.string()", strArgClass));
        brigadierRetrievals.put("java.lang.String", a -> CodeBlock.of("$T.getString(ctx, $S)", strArgClass, a));

        registerBrigadierType("int", "java.lang.Integer", intArgClass, "integer", "getInteger");
        registerBrigadierType("long", "java.lang.Long", longArgClass, "longArg", "getLong");
        registerBrigadierType("double", "java.lang.Double", dblArgClass, "doubleArg", "getDouble");
        registerBrigadierType("float", "java.lang.Float", fltArgClass, "floatArg", "getFloat");
        registerBrigadierType("boolean", "java.lang.Boolean", boolArgClass, "bool", "getBool");

        brigadierArgTypes.put("org.bukkit.entity.Player", CodeBlock.of("$L.player()", argumentTypesClass));
        brigadierRetrievals.put("org.bukkit.entity.Player", a -> CodeBlock.of("ctx.getArgument($S, $T.class).resolve(ctx.getSource()).iterator().next()", a, playerResolverClass));
        brigadierArgTypes.put("org.bukkit.World", CodeBlock.of("$L.world()", argumentTypesClass));
        brigadierRetrievals.put("org.bukkit.World", a -> CodeBlock.of("ctx.getArgument($S, $T.class)", a, worldClass));
        brigadierArgTypes.put("org.bukkit.Location", CodeBlock.of("$L.finePosition(true)", argumentTypesClass));
        brigadierRetrievals.put("org.bukkit.Location", a -> CodeBlock.of("ctx.getArgument($S, $T.class).resolve(ctx.getSource()).toLocation(ctx.getSource().getLocation().getWorld())", a, finePositionClass));
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
    }

    @Override
    protected void onBeforeExecute(MethodSpec.Builder methodSpec, Element element, String returnStatement) {
        Permission permission = findPermissionUp(element);
        if (permission != null) {
            methodSpec.beginControlFlow("if (!sender.getSender().hasPermission($S))", permission.value());
            String msg = permission.message();
            if (!msg.isEmpty() && isI18nKey(msg)) {
                methodSpec.addStatement("sender.getSender().sendMessage($T.text(manager.formatMessage($S, $S, $S), $T.RED))",
                        componentClass, i18nKey(msg), msg, permission.value(), errorColorClass);
            } else if (!msg.isEmpty()) {
                methodSpec.addStatement("sender.getSender().sendMessage($T.text($S, $T.RED))",
                        componentClass, msg, errorColorClass);
            } else {
                methodSpec.addStatement("sender.getSender().sendMessage($T.text(manager.formatMessage($S, $S, $S), $T.RED))",
                        componentClass, "permission", "You do not have permission to execute this command.", permission.value(), errorColorClass);
            }
            methodSpec.addStatement("return $T.SINGLE_SUCCESS", commandClass);
            methodSpec.endControlFlow();
        }
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
            String subBuilderVar = "subBuilder_" + Naming.sanitizeIdentifier(sub.getSubcommandName());
            spec.addStatement("$T $L = $T.literal($S)",
                    ParameterizedTypeName.get(literalArgumentBuilderClass, commandSourceStackClass),
                    subBuilderVar, commandsClass, sub.getSubcommandName());
            buildMethodParametersTree(spec, subBuilderVar, model, sub, instanceExpr, rootModel);
            spec.addStatement("$L.then($L)", builderVar, subBuilderVar);
        }
        if (model.getDefaultMethod() != null) {
            buildMethodParametersTree(spec, builderVar, model, model.getDefaultMethod(), instanceExpr, rootModel);
        }
    }

    private void buildMethodParametersTree(MethodSpec.Builder spec, String parentBuilderVar, CommandModel classModel, MethodModel method, String instanceExpr, CommandModel rootModel) {
        List<NodeInfo> nodes = new ArrayList<>();
        for (ParameterModel p : method.getParameters()) {
            if (p == method.getSenderParameter()) continue;
            MethodModel resolverModel = p.getResolverMethod();
            if (resolverModel != null) {
                ExecutableElement localResolver = resolverModel.getElement();
                List<ParameterModel> resolverParams = resolverModel.getParameters();
                int resolverStartIndex = firstParamIsSender(localResolver, method) ? 1 : 0;
                int resolverWidth = resolverParams.size() - resolverStartIndex;
                for (int i = 0; i < resolverWidth; i++) {
                    ParameterModel rp = resolverParams.get(resolverStartIndex + i);
                    TypeName rpTypeName = TypeName.get(rp.getType());
                    if (isSenderParam(rpTypeName, method)) {
                        continue;
                    }
                    String rpName = rp.getName();
                    CodeBlock typeBlock = getArgumentTypeExpressionFromTypeName(rpTypeName, rp.isGreedy());
                    String suggestProvider = rp.getSuggestProvider() != null ? rp.getSuggestProvider() : p.getSuggestProvider();
                    MethodModel suggestMethod = rp.getSuggestProvider() != null ? rp.getSuggestMethod() : p.getSuggestMethod();
                    boolean suggestField = rp.getSuggestProvider() != null ? rp.isSuggestField() : p.isSuggestField();
                    nodes.add(new NodeInfo(rpName, typeBlock, p, i, i == resolverWidth - 1, rp.isOptional(),
                            suggestProvider, rpTypeName, suggestMethod, suggestField));
                }
            } else {
                int width = getParameterWidth(p, method);
                CodeBlock typeBlock = getArgumentTypeExpression(p);
                if (width <= 1) {
                    nodes.add(new NodeInfo(p.getName(), typeBlock, p, 0, true, false,
                            p.getSuggestProvider(), null, p.getSuggestMethod(), p.isSuggestField()));
                } else {
                    for (int i = 0; i < width; i++) {
                        nodes.add(new NodeInfo(p.getName() + "_" + i,
                                CodeBlock.of("$T.string()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType")),
                                p, i, i == width - 1, false,
                                p.getSuggestProvider(), null, p.getSuggestMethod(), p.isSuggestField()));
                    }
                }
            }
        }
        buildNodeChainRecursive(spec, parentBuilderVar, classModel, method, instanceExpr, rootModel, nodes, 0);
    }

    private int getParameterWidth(ParameterModel param, MethodModel method) {
        MethodModel resolverModel = param.getResolverMethod();
        if (resolverModel != null) return getLocalResolverMaxWidth(resolverModel, method);
        return getBuiltInWidth(TypeName.get(param.getType()));
    }

    private void buildNodeChainRecursive(MethodSpec.Builder spec, String currentBuilderVar, CommandModel classModel, MethodModel method, String instanceExpr, CommandModel rootModel, List<NodeInfo> nodes, int index) {
        boolean canExecuteHere = false;
        if (index == nodes.size()) {
            canExecuteHere = true;
        } else if (index == 0) {
            if (nodes.get(0).parameter.isOptional() || nodes.get(0).isResolverParamOptional) {
                canExecuteHere = true;
            }
        } else if (nodes.get(index - 1).isLastForParameter) {
            if (nodes.get(index).parameter.isOptional()) canExecuteHere = true;
        } else if (nodes.get(index).isResolverParamOptional) {
            canExecuteHere = true;
        }

        if (canExecuteHere) {
            spec.beginControlFlow("$L.executes(ctx ->", currentBuilderVar);
            generateExecutionBlock(spec, classModel, method, instanceExpr, rootModel, nodes, index);
            spec.addCode("$<});\n");
        }

        if (index < nodes.size()) {
            NodeInfo node = nodes.get(index);
            String nextBuilderVar = "argBuilder_" + Naming.sanitizeIdentifier(method.getMethodName()) + "_" + Naming.sanitizeIdentifier(node.nodeName);
            spec.addStatement("$T $L = $T.argument($S, $L)",
                    ParameterizedTypeName.get(requiredArgumentBuilderClass, commandSourceStackClass, WildcardTypeName.subtypeOf(TypeName.get(Object.class))),
                    nextBuilderVar, commandsClass, node.nodeName, node.typeExpression);

            ParameterModel p = node.parameter;
            TypeName parameterType = TypeName.get(p.getType());
            boolean needsSuggestions = node.suggestProvider != null || TypeSupport.isBooleanType(parameterType);
            if (!needsSuggestions && node.resolverParamType != null) {
                TypeName rpType = node.resolverParamType;
                if (TypeSupport.isBooleanType(rpType)) {
                    needsSuggestions = true;
                } else if (typeSupport().hasPlatformSuggestions(rpType)) {
                    needsSuggestions = true;
                }
            }

            if (needsSuggestions && (node.isLastForParameter || node.resolverParamType != null)) {
                if (node.suggestProvider != null) {
                    if (node.suggestField) {
                        spec.addStatement("$L.suggests((ctx, sb) -> $T.suggestMatching(ctx, sb, args -> $T.filterSuggestions($L.$L, args[args.length - 1])))",
                                nextBuilderVar, paperSuggestionsClass, ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, node.suggestProvider);
                    } else {
                        CodeBlock suggestSenderExpr = CodeBlock.of("ctx.getSource()");
                        boolean needsCast = false;
                        MethodModel suggestMethod = node.suggestMethod;
                        if (suggestMethod != null && !suggestMethod.getParameters().isEmpty()) {
                            ParameterModel firstParam = suggestMethod.getParameters().get(0);
                            TypeName firstParamType = TypeName.get(firstParam.getType());
                            if (!firstParamType.equals(getSenderTypeName())) {
                                String firstParamResolve = firstParam.getResolveName();
                                if (firstParamResolve != null) {
                                    if (!firstParamResolve.isEmpty()) {
                                        MethodModel resolver = firstParam.getResolverMethod();
                                        if (resolver != null) {
                                            String resolverInstanceExpr = getInstanceVarExpression(classModel, rootModel);
                                            suggestSenderExpr = BaseCommandGenerator.call(resolverInstanceExpr,
                                                    resolver.getElement().getSimpleName().toString(), CodeBlock.of("ctx.getSource()"));
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
                                ? getSuggestCallExpr(suggestMethod, method, instanceExpr, node.suggestProvider, suggestSenderExpr)
                                : BaseCommandGenerator.call(instanceExpr, node.suggestProvider);
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
                    String resolveName = p.getResolveName();
                    if (resolveName != null && !resolveName.isEmpty()) {
                        String helperName = getResolverParamSuggestionMethodName(classModel, method, resolveName, node.resolverArgIndex);
                        spec.addStatement("$L.suggests((ctx, sb) -> $T.suggestMatching(ctx, sb, args -> $L(ctx.getSource(), args)))",
                                nextBuilderVar, paperSuggestionsClass, helperName);
                    } else {
                        int paramIndex = method.getParameters().indexOf(p);
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
        onBeforeExecute(spec, method.getElement(), "return");
        new PaperExecutionSource(this, nodes, parsedNodeCount).generateExecution(spec, classModel, method, instanceExpr, rootModel);
        spec.nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("manager.getErrorHandler().accept(sender, e)")
                .endControlFlow();
        spec.addStatement("return $T.SINGLE_SUCCESS", commandClass);
    }

    private CodeBlock getArgumentTypeExpression(ParameterModel param) {
        return getArgumentTypeExpressionFromTypeName(TypeName.get(param.getType()), param.isGreedy());
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
        int argCount = suggestMethod.getParameters().size();
        if (argCount == 0) {
            return BaseCommandGenerator.call(instanceExpr, suggestProvider);
        }
        CodeBlock current = CodeBlock.of("new String[]{args[args.length - 1]}");
        if (argCount == 1) {
            TypeMirror firstParamType = suggestMethod.getParameters().get(0).getType();
            return TypeNames.isStringArray(firstParamType)
                    ? BaseCommandGenerator.call(instanceExpr, suggestProvider, current)
                    : BaseCommandGenerator.call(instanceExpr, suggestProvider, suggestSenderExpr);
        }
        if (argCount == 2) {
            TypeMirror firstParamType = suggestMethod.getParameters().get(0).getType();
            return TypeNames.isStringArray(firstParamType)
                    ? BaseCommandGenerator.call(instanceExpr, suggestProvider, current, CodeBlock.of("args"))
                    : BaseCommandGenerator.call(instanceExpr, suggestProvider, suggestSenderExpr, current);
        }
        if (argCount == 3) {
            return BaseCommandGenerator.call(instanceExpr, suggestProvider, suggestSenderExpr, current, CodeBlock.of("args"));
        }
        return BaseCommandGenerator.call(instanceExpr, suggestProvider);
    }

    static final class NodeInfo {
        final String nodeName;
        final CodeBlock typeExpression;
        final ParameterModel parameter;
        final int resolverArgIndex;
        final boolean isLastForParameter;
        final boolean isResolverParamOptional;
        final String suggestProvider;
        final TypeName resolverParamType;
        final MethodModel suggestMethod;
        final boolean suggestField;

        NodeInfo(String nodeName, CodeBlock typeExpression, ParameterModel parameter, int resolverArgIndex,
                 boolean isLastForParameter, boolean isResolverParamOptional, String suggestProvider,
                 TypeName resolverParamType, MethodModel suggestMethod, boolean suggestField) {
            this.nodeName = nodeName;
            this.typeExpression = typeExpression;
            this.parameter = parameter;
            this.resolverArgIndex = resolverArgIndex;
            this.isLastForParameter = isLastForParameter;
            this.isResolverParamOptional = isResolverParamOptional;
            this.suggestProvider = suggestProvider;
            this.resolverParamType = resolverParamType;
            this.suggestMethod = suggestMethod;
            this.suggestField = suggestField;
        }
    }
}
