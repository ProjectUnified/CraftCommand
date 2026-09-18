package io.github.projectunified.craftcommand.processor.paper;

import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.BaseCommandProcessor;
import io.github.projectunified.craftcommand.processor.CommandPrism;
import io.github.projectunified.craftcommand.processor.ResolverLookup;
import io.github.projectunified.craftcommand.processor.TypeSupport;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.TypeMirror;
import java.util.*;
import java.util.function.Function;

@SupportedAnnotationTypes(CommandPrism.PRISM_TYPE)
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PaperCommandProcessor extends BaseCommandProcessor {

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

    private final Map<String, Function<Boolean, CodeBlock>> brigadierArgTypes = new HashMap<>();
    private final Map<String, Function<String, CodeBlock>> brigadierRetrievals = new HashMap<>();

    {
        senderTypeRegistry().registerSenderBaseType("io.papermc.paper.command.brigadier.CommandSourceStack");
        senderTypeRegistry().registerSenderType("org.bukkit.entity.Player");
        senderTypeRegistry().registerSenderType("org.bukkit.command.ConsoleCommandSender");
        senderTypeRegistry().registerSenderType("org.bukkit.command.BlockCommandSender");
        senderTypeRegistry().registerSenderType("org.bukkit.command.CommandSender");

        ClassName strArgClass = ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType");
        ClassName intArgClass = ClassName.get("com.mojang.brigadier.arguments", "IntegerArgumentType");
        ClassName longArgClass = ClassName.get("com.mojang.brigadier.arguments", "LongArgumentType");
        ClassName dblArgClass = ClassName.get("com.mojang.brigadier.arguments", "DoubleArgumentType");
        ClassName fltArgClass = ClassName.get("com.mojang.brigadier.arguments", "FloatArgumentType");
        ClassName boolArgClass = ClassName.get("com.mojang.brigadier.arguments", "BoolArgumentType");
        ClassName playerResolverClass = ClassName.get("io.papermc.paper.command.brigadier.argument.resolvers.selector", "PlayerSelectorArgumentResolver");
        ClassName worldClass = ClassName.get("org.bukkit", "World");
        ClassName finePositionClass = ClassName.get("io.papermc.paper.command.brigadier.argument.resolvers", "FinePositionResolver");

        // String (special: greedy variant)
        brigadierArgTypes.put("java.lang.String", g -> CodeBlock.of("$T.$L()", strArgClass, g ? "greedyString" : "string"));
        brigadierRetrievals.put("java.lang.String", a -> CodeBlock.of("$T.getString(ctx, $S)", strArgClass, a));

        // Primitive/wrapper pairs
        registerBrigadierType("int", "java.lang.Integer", intArgClass, "integer", "getInteger");
        registerBrigadierType("long", "java.lang.Long", longArgClass, "longArg", "getLong");
        registerBrigadierType("double", "java.lang.Double", dblArgClass, "doubleArg", "getDouble");
        registerBrigadierType("float", "java.lang.Float", fltArgClass, "floatArg", "getFloat");
        registerBrigadierType("boolean", "java.lang.Boolean", boolArgClass, "bool", "getBool");

        // Platform types
        brigadierArgTypes.put("org.bukkit.entity.Player", g -> CodeBlock.of("$L.player()", argumentTypesClass));
        brigadierRetrievals.put("org.bukkit.entity.Player", a -> CodeBlock.of("ctx.getArgument($S, $T.class).resolve(ctx.getSource()).iterator().next()", a, playerResolverClass));
        brigadierArgTypes.put("org.bukkit.World", g -> CodeBlock.of("$L.world()", argumentTypesClass));
        brigadierRetrievals.put("org.bukkit.World", a -> CodeBlock.of("ctx.getArgument($S, $T.class)", a, worldClass));
        brigadierArgTypes.put("org.bukkit.Location", g -> CodeBlock.of("$L.finePosition(true)", argumentTypesClass));
        brigadierRetrievals.put("org.bukkit.Location", a -> CodeBlock.of("ctx.getArgument($S, $T.class).resolve(ctx.getSource()).toLocation(ctx.getSource().getLocation().getWorld())", a, finePositionClass));
    }

    private static PermissionPrism findPermissionUp(Element element) {
        Element current = element;
        while (current != null) {
            PermissionPrism permission = PermissionPrism.getInstanceOn(current);
            if (permission != null) return permission;
            current = current.getEnclosingElement();
        }
        return null;
    }

    private void registerBrigadierType(String primitive, String wrapper, ClassName argClass, String argMethod, String retrievalMethod) {
        Function<Boolean, CodeBlock> argType = g -> CodeBlock.of("$T.$L()", argClass, argMethod);
        Function<String, CodeBlock> retrieval = a -> CodeBlock.of("$T.$L(ctx, $S)", argClass, retrievalMethod, a);
        brigadierArgTypes.put(primitive, argType);
        brigadierArgTypes.put(wrapper, argType);
        brigadierRetrievals.put(primitive, retrieval);
        brigadierRetrievals.put(wrapper, retrieval);
    }

    @Override
    protected void registerTypes(TypeSupport types) {
        ClassName playerClass = ClassName.get("org.bukkit.entity", "Player");
        ClassName worldClass = ClassName.get("org.bukkit", "World");
        ClassName locationClass = ClassName.get("org.bukkit", "Location");
        types.register(TypeSupport.Entry.builder(playerClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null")).build());
        types.register(TypeSupport.Entry.builder(worldClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null")).build());
        types.register(TypeSupport.Entry.builder(locationClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null")).build());
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
    protected ClassName getSenderTypeName() {
        return commandSourceStackClass;
    }

    @Override
    protected TypeName getManagerType() {
        ClassName commandManagerClass = ClassName.get("io.github.projectunified.craftcommand", "CommandManager");
        return ParameterizedTypeName.get(commandManagerClass, commandSourceStackClass);
    }

    @Override
    protected CodeBlock getSenderExpression(String senderVar) {
        return CodeBlock.of("$L.getSender()", senderVar);
    }

    @Override
    protected void generateEntryMethods(TypeSpec.Builder typeSpec, CommandModel model, TypeElement typeElement) {
        typeSpec.addMethod(MethodSpec.methodBuilder("getDescription")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(String.class)
                .addStatement("return $S", model.getDescription()).build());
        typeSpec.addMethod(MethodSpec.methodBuilder("getAliases")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(Collection.class, String.class))
                .addStatement("return $L", buildAliasesExpression(model)).build());

        // getCommandNode()
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
    }

    @Override
    protected void onBeforeExecute(MethodSpec.Builder methodSpec, Element element, String returnStatement) {
        PermissionPrism permission = findPermissionUp(element);
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

    // ── Brigadier tree generation (private helpers) ──

    private void buildBrigadierTree(MethodSpec.Builder spec, CommandModel model, String builderVar, String instanceExpr, CommandModel rootModel) {
        for (CommandModel child : model.getNestedSubcommands()) {
            String childInstanceVar = "this." + getSubcommandFieldName(child);
            String childBuilderVar = "subBuilder_" + sanitizeIdentifier(child.getCommandName());
            spec.addStatement("$T $L = $T.literal($S)",
                    ParameterizedTypeName.get(literalArgumentBuilderClass, commandSourceStackClass),
                    childBuilderVar, commandsClass, child.getCommandName());
            buildBrigadierTree(spec, child, childBuilderVar, childInstanceVar, rootModel);
            spec.addStatement("$L.then($L)", builderVar, childBuilderVar);
        }
        for (MethodModel sub : model.getSubcommands()) {
            String subBuilderVar = "subBuilder_" + sanitizeIdentifier(sub.getSubcommandName());
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
        List<ParameterModel> cmdArgs = new ArrayList<>();
        for (ParameterModel p : method.getParameters()) {
            if (p == method.getSenderParameter()) continue;
            cmdArgs.add(p);
        }

        List<NodeInfo> nodes = new ArrayList<>();
        for (ParameterModel p : cmdArgs) {
            MethodModel resolverModel = p.getResolverMethod();
            if (resolverModel != null) {
                ExecutableElement localResolver = resolverModel.getElement();
                List<ParameterModel> resolverParams = resolverModel.getParameters();
                int resolverStartIndex = firstParamIsSender(localResolver) ? 1 : 0;
                int resolverWidth = resolverParams.size() - resolverStartIndex;
                for (int i = 0; i < resolverWidth; i++) {
                    ParameterModel rp = resolverParams.get(resolverStartIndex + i);
                    TypeName rpTypeName = TypeName.get(rp.getType());
                    if (isSenderParam(rpTypeName, method)) {
                        continue;
                    }
                    String rpName = rp.getElement().getSimpleName().toString();
                    CodeBlock typeBlock = getArgumentTypeExpressionFromTypeName(rpTypeName, rp.isGreedy());
                    String rpSuggestProvider = rp.getSuggestProvider() != null ? rp.getSuggestProvider() : p.getSuggestProvider();
                    MethodModel rpSuggestMethod = rp.getSuggestProvider() != null ? rp.getSuggestMethod() : p.getSuggestMethod();
                    nodes.add(new NodeInfo(rpName, typeBlock, p, i, i == resolverWidth - 1, rp.isOptional(), rpSuggestProvider, rpTypeName, rpSuggestMethod));
                }
            } else {
                int width = getParameterWidth(p, method);
                CodeBlock typeBlock = getArgumentTypeExpression(p);
                if (width <= 1) {
                    nodes.add(new NodeInfo(p.getName(), typeBlock, p, 0, true, false, p.getSuggestProvider(), null, p.getSuggestMethod()));
                } else {
                    for (int i = 0; i < width; i++) {
                        nodes.add(new NodeInfo(p.getName() + "_" + i,
                                CodeBlock.of("$T.string()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType")),
                                p, i, i == width - 1, false, p.getSuggestProvider(), null, p.getSuggestMethod()));
                    }
                }
            }
        }
        buildNodeChainRecursive(spec, parentBuilderVar, classModel, method, instanceExpr, rootModel, nodes, 0);
    }

    private int getParameterWidth(ParameterModel param, MethodModel method) {
        MethodModel resolverModel = param.getResolverMethod();
        if (resolverModel != null) return getLocalResolverMaxWidth(resolverModel, method);
        TypeName typeName = TypeName.get(param.getType());
        if (isPlatformBuiltInType(typeName)) return getBuiltInWidth(typeName);
        return 1;
    }

    private void buildNodeChainRecursive(MethodSpec.Builder spec, String currentBuilderVar, CommandModel classModel, MethodModel method, String instanceExpr, CommandModel rootModel, List<NodeInfo> nodes, int index) {
        boolean canExecuteHere = false;
        if (index == nodes.size()) {
            canExecuteHere = true;
        } else if (index == 0) {
            if (nodes.get(0).parameter.isOptional() || nodes.get(0).isResolverParamOptional) {
                canExecuteHere = true;
            }
        } else if (index > 0 && nodes.get(index - 1).isLastForParameter) {
            ParameterModel nextParam = nodes.get(index).parameter;
            if (nextParam.isOptional()) canExecuteHere = true;
        } else if (index > 0 && nodes.get(index).isResolverParamOptional) {
            canExecuteHere = true;
        }

        if (canExecuteHere) {
            spec.beginControlFlow("$L.executes(ctx ->", currentBuilderVar);
            generateExecutionBlock(spec, classModel, method, instanceExpr, rootModel, nodes, index);
            spec.addCode("$<});\n");
        }

        if (index < nodes.size()) {
            NodeInfo node = nodes.get(index);
            String nextBuilderVar = "argBuilder_" + sanitizeIdentifier(method.getMethodName()) + "_" + sanitizeIdentifier(node.nodeName);
            spec.addStatement("$T $L = $T.argument($S, $L)",
                    ParameterizedTypeName.get(requiredArgumentBuilderClass, commandSourceStackClass, WildcardTypeName.subtypeOf(TypeName.get(Object.class))),
                    nextBuilderVar, commandsClass, node.nodeName, node.typeExpression);

            ParameterModel p = node.parameter;
            boolean needsSuggestions = node.suggestProvider != null || p.getSuggestProvider() != null || TypeName.get(p.getType()).toString().equals("boolean") || TypeName.get(p.getType()).toString().equals("java.lang.Boolean");
            // Also check resolver param type for platform built-in suggestions (e.g. World)
            if (!needsSuggestions && node.resolverParamType != null) {
                TypeName rpType = node.resolverParamType;
                if (rpType.toString().equals("boolean") || rpType.toString().equals("java.lang.Boolean")) {
                    needsSuggestions = true;
                } else if (isPlatformBuiltInType(rpType)) {
                    needsSuggestions = true;
                }
            }

            if (needsSuggestions && node.isLastForParameter) {
                String suggestProvider = node.suggestProvider != null ? node.suggestProvider : p.getSuggestProvider();
                if (suggestProvider != null) {
                    // Custom suggest provider — check if it's a field or method
                    TypeElement typeElement = classModel.getElement();
                    if (isField(typeElement, suggestProvider)) {
                        // Field: access directly on instance
                        spec.addStatement("$L.suggests((ctx, sb) -> $T.suggestMatching(ctx, sb, args -> $T.filterSuggestions($L.$L, args[args.length - 1])))",
                                nextBuilderVar, paperSuggestionsClass, ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), instanceExpr, suggestProvider);
                    } else {
                        // Method: invoke on command instance — get correct sender expression
                        String suggestSenderExpr = "ctx.getSource()";
                        MethodModel suggestMethod = node.suggestProvider != null ? node.suggestMethod : p.getSuggestMethod();
                        boolean needsCast = false;
                        if (suggestMethod != null && !suggestMethod.getParameters().isEmpty()) {
                            ParameterModel firstParam = suggestMethod.getParameters().get(0);
                            TypeName firstParamType = TypeName.get(firstParam.getType());
                            if (!firstParamType.toString().equals(getSenderTypeName().toString())) {
                                // Check if first param has @Resolve annotation - it's a custom sender
                                String firstParamResolve = firstParam.getResolveName();
                                if (firstParamResolve != null) {
                                    if (!firstParamResolve.isEmpty()) {
                                        // Local resolver method
                                        ExecutableElement resolver = ResolverLookup.findMethod(typeElement, firstParamResolve);
                                        if (resolver != null) {
                                            String resolverInstanceExpr = getInstanceVarExpression(classModel, rootModel);
                                            suggestSenderExpr = String.format("%s.%s(ctx.getSource())", resolverInstanceExpr, resolver.getSimpleName().toString());
                                        }
                                    } else {
                                        // Global sender resolver
                                        suggestSenderExpr = String.format("(%s) manager.resolveSender(%s.class, ctx.getSource())", firstParamType, firstParamType);
                                    }
                                } else if (isSenderType(firstParamType)) {
                                    suggestSenderExpr = "as" + getSimpleName(firstParamType) + "(ctx.getSource())";
                                    needsCast = true;
                                }
                            }
                        }
                        String callExpr = suggestMethod != null
                                ? getSuggestCallExpr(suggestMethod, method, instanceExpr, suggestProvider, suggestSenderExpr)
                                : String.format("%s.%s()", instanceExpr, suggestProvider);
                        if (needsCast) {
                            // Wrap in try-catch to handle non-player senders gracefully during tab completion
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
                    // Boolean or platform built-in — use standard suggestion helper
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
        PaperExecutionSource source = new PaperExecutionSource(this, nodes, parsedNodeCount);
        source.generateExecution(spec, classModel, method, instanceExpr, rootModel);
        spec.nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("manager.getErrorHandler().accept(sender, e)")
                .endControlFlow();
        spec.addStatement("return $T.SINGLE_SUCCESS", commandClass);
    }

    private CodeBlock getArgumentTypeExpression(ParameterModel param) {
        if (param.getResolverMethod() != null) {
            return CodeBlock.of("$T.string()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"));
        }
        return getArgumentTypeExpressionFromTypeName(TypeName.get(param.getType()), param.isGreedy());
    }

    private CodeBlock getArgumentTypeExpressionFromTypeName(TypeName typeName, boolean isGreedy) {
        if (isGreedy)
            return CodeBlock.of("$T.greedyString()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"));
        Function<Boolean, CodeBlock> provider = brigadierArgTypes.get(typeName.toString());
        return provider != null ? provider.apply(false) : CodeBlock.of("$T.string()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"));
    }

    CodeBlock getArgumentRetrievalExpression(TypeName typeName, String argName) {
        Function<String, CodeBlock> provider = brigadierRetrievals.get(typeName.toString());
        return provider != null ? provider.apply(argName) : CodeBlock.of("ctx.getArgument($S, $T.class)", argName, typeName);
    }

    private String getSuggestCallExpr(MethodModel suggestMethod, MethodModel method, String instanceExpr, String suggestProvider, String suggestSenderExpr) {
        int argCount = suggestMethod.getParameters().size();
        if (argCount == 0) {
            return String.format("%s.%s()", instanceExpr, suggestProvider);
        } else if (argCount == 1) {
            TypeMirror firstParamType = suggestMethod.getParameters().get(0).getType();
            if (isSenderParam(TypeName.get(firstParamType), method)) {
                return String.format("%s.%s(%s)", instanceExpr, suggestProvider, suggestSenderExpr);
            } else {
                return String.format("%s.%s(args)", instanceExpr, suggestProvider);
            }
        } else if (argCount == 2) {
            return String.format("%s.%s(%s, args)", instanceExpr, suggestProvider, suggestSenderExpr);
        } else if (argCount == 3) {
            return String.format("%s.%s(%s, args, args)", instanceExpr, suggestProvider, suggestSenderExpr);
        }
        return String.format("%s.%s()", instanceExpr, suggestProvider);
    }

    private String sanitizeIdentifier(String name) {
        return name.replace("-", "_").replace(" ", "_");
    }

    static class NodeInfo {
        final String nodeName;
        final CodeBlock typeExpression;
        final ParameterModel parameter;
        final int resolverArgIndex;
        final boolean isLastForParameter;
        final boolean isResolverParamOptional;
        final String suggestProvider;
        final MethodModel suggestMethod;
        final TypeName resolverParamType;

        NodeInfo(String nodeName, CodeBlock typeExpression, ParameterModel parameter, int resolverArgIndex, boolean isLastForParameter, boolean isResolverParamOptional, String suggestProvider) {
            this(nodeName, typeExpression, parameter, resolverArgIndex, isLastForParameter, isResolverParamOptional, suggestProvider, null);
        }

        NodeInfo(String nodeName, CodeBlock typeExpression, ParameterModel parameter, int resolverArgIndex, boolean isLastForParameter, boolean isResolverParamOptional, String suggestProvider, TypeName resolverParamType) {
            this(nodeName, typeExpression, parameter, resolverArgIndex, isLastForParameter, isResolverParamOptional, suggestProvider, resolverParamType, null);
        }

        NodeInfo(String nodeName, CodeBlock typeExpression, ParameterModel parameter, int resolverArgIndex, boolean isLastForParameter, boolean isResolverParamOptional, String suggestProvider, TypeName resolverParamType, MethodModel suggestMethod) {
            this.nodeName = nodeName;
            this.typeExpression = typeExpression;
            this.parameter = parameter;
            this.resolverArgIndex = resolverArgIndex;
            this.isLastForParameter = isLastForParameter;
            this.isResolverParamOptional = isResolverParamOptional;
            this.suggestProvider = suggestProvider;
            this.resolverParamType = resolverParamType;
            this.suggestMethod = suggestMethod;
        }
    }
}
