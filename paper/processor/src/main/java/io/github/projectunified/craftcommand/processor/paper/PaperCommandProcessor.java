package io.github.projectunified.craftcommand.processor.paper;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.annotation.Greedy;
import io.github.projectunified.craftcommand.annotation.Optional;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.github.projectunified.craftcommand.processor.BaseCommandProcessor;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.List;

/**
 * Annotation processor for Paper Brigadier platform.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.projectunified.craftcommand.annotation.Command")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PaperCommandProcessor extends BaseCommandProcessor {

    final ClassName commandSourceStackClass = ClassName.get("io.papermc.paper.command.brigadier", "CommandSourceStack");
    final ClassName literalCommandNodeClass = ClassName.get("com.mojang.brigadier.tree", "LiteralCommandNode");
    final ClassName commandsClass = ClassName.get("io.papermc.paper.command.brigadier", "Commands");
    final ClassName commandClass = ClassName.get("com.mojang.brigadier", "Command");
    final ClassName commandContextClass = ClassName.get("com.mojang.brigadier.context", "CommandContext");
    final ClassName requiredArgumentBuilderClass = ClassName.get("com.mojang.brigadier.builder", "RequiredArgumentBuilder");
    final ClassName literalArgumentBuilderClass = ClassName.get("com.mojang.brigadier.builder", "LiteralArgumentBuilder");
    final ClassName argumentTypesClass = ClassName.get("io.papermc.paper.command.brigadier.argument", "ArgumentTypes");
    final ClassName errorColorClass = ClassName.get("net.kyori.adventure.text.format", "NamedTextColor");
    final ClassName componentClass = ClassName.get("net.kyori.adventure.text", "Component");

    @Override
    protected String getWrapperClassSuffix() {
        return "_Paper";
    }

    @Override
    protected void configureSuperType(TypeSpec.Builder typeSpec) {
        typeSpec.addSuperinterface(ClassName.get("io.github.projectunified.craftcommand.paper", "PaperCommand"));
    }

    @Override
    protected TypeName getCommandInterfaceType() {
        return ClassName.get("io.github.projectunified.craftcommand.paper", "PaperCommand");
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
    protected boolean isSenderType(TypeName typeName) {
        String name = typeName.toString();
        return name.equals("io.papermc.paper.command.brigadier.CommandSourceStack")
                || name.equals("org.bukkit.entity.Player")
                || name.equals("org.bukkit.command.ConsoleCommandSender")
                || name.equals("org.bukkit.command.BlockCommandSender")
                || name.equals("org.bukkit.command.CommandSender");
    }

    @Override
    protected boolean isSenderBaseType(TypeName typeName) {
        String name = typeName.toString();
        return name.equals("io.papermc.paper.command.brigadier.CommandSourceStack");
    }

    @Override
    protected boolean isPlatformBuiltInType(TypeName typeName) {
        String name = typeName.toString();
        return name.equals("org.bukkit.entity.Player")
                || name.equals("org.bukkit.World")
                || name.equals("org.bukkit.Location");
    }

    @Override
    protected int getPlatformBuiltInWidth(TypeName typeName) {
        String name = typeName.toString();
        return 1;
    }

    @Override
    protected void generatePlatformParamSuggestions(MethodSpec.Builder methodSpec, TypeName typeName, String senderCastVar, String argsVar, String currentVar, int tempIdx) {
        methodSpec.addStatement("return $T.emptyList()", Collections.class);
    }

    @Override
    protected CodeBlock getSenderExpression(String senderVar) {
        return CodeBlock.of("$L.getSender()", senderVar);
    }

    @Override
    protected void buildEntryMethods(TypeSpec.Builder typeSpec, CommandModel model, TypeElement typeElement) {
        // getDescription()
        typeSpec.addMethod(MethodSpec.methodBuilder("getDescription")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", model.getDescription())
                .build());

        // getAliases()
        typeSpec.addMethod(MethodSpec.methodBuilder("getAliases")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(Collection.class, String.class))
                .addStatement("return $L", buildAliasesExpression(model))
                .build());

        // getCommandNode()
        MethodSpec.Builder getCommandNodeSpec = MethodSpec.methodBuilder("getCommandNode")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(literalCommandNodeClass, commandSourceStackClass));

        getCommandNodeSpec.addStatement("$T builder = $T.literal($S)",
                ParameterizedTypeName.get(literalArgumentBuilderClass, commandSourceStackClass),
                commandsClass,
                model.getCommandName());

        buildBrigadierTree(getCommandNodeSpec, model, "builder", "instance", model);

        getCommandNodeSpec.addStatement("return builder.build()");
        typeSpec.addMethod(getCommandNodeSpec.build());

        // Generate generic suggestions helper
        generateSuggestionsHelper(typeSpec);
    }

    private void generateSuggestionsHelper(TypeSpec.Builder typeSpec) {
        ClassName completableFutureClass = ClassName.get("java.util.concurrent", "CompletableFuture");
        ClassName suggestionsClass = ClassName.get("com.mojang.brigadier.suggestion", "Suggestions");
        ClassName suggestionsBuilderClass = ClassName.get("com.mojang.brigadier.suggestion", "SuggestionsBuilder");
        ClassName functionClass = ClassName.get("java.util.function", "Function");
        ClassName listClass = ClassName.get("java.util", "List");
        ClassName arrayListClass = ClassName.get("java.util", "ArrayList");

        MethodSpec.Builder mb = MethodSpec.methodBuilder("getSuggestions")
                .addModifiers(Modifier.PRIVATE)
                .returns(ParameterizedTypeName.get(completableFutureClass, suggestionsClass))
                .addParameter(ParameterizedTypeName.get(commandContextClass, commandSourceStackClass), "ctx")
                .addParameter(suggestionsBuilderClass, "builder")
                .addParameter(ParameterizedTypeName.get(functionClass, ArrayTypeName.of(String.class), ParameterizedTypeName.get(listClass, ClassName.get(String.class))), "provider");

        mb.addStatement("$T input = ctx.getInput()", String.class)
                .addStatement("$T remaining = builder.getRemaining()", String.class)
                .addStatement("$T<String> argsList = new $T<>()", listClass, arrayListClass)
                .addStatement("$T sb = new $T()", StringBuilder.class, StringBuilder.class)
                .beginControlFlow("for (int i = 0; i < input.length(); i++)")
                .addStatement("char c = input.charAt(i)")
                .beginControlFlow("if (c == ' ')")
                .addStatement("argsList.add(sb.toString())")
                .addStatement("sb.setLength(0)")
                .nextControlFlow("else")
                .addStatement("sb.append(c)")
                .endControlFlow()
                .endControlFlow()
                .addStatement("argsList.add(sb.toString())")
                .beginControlFlow("if (!argsList.isEmpty() && (argsList.get(0).startsWith(\"/\") || argsList.get(0).equalsIgnoreCase(ctx.getRootNode().getName())))")
                .addStatement("argsList.remove(0)")
                .endControlFlow()
                .addStatement("$T[] args = argsList.toArray(new $T[0])", String.class, String.class)
                .beginControlFlow("for ($T suggestion : provider.apply(args))", String.class)
                .beginControlFlow("if (suggestion.toLowerCase().startsWith(remaining.toLowerCase()))")
                .addStatement("builder.suggest(suggestion)")
                .endControlFlow()
                .endControlFlow()
                .addStatement("return builder.buildFuture()");

        typeSpec.addMethod(mb.build());
    }

    private void buildBrigadierTree(MethodSpec.Builder spec, CommandModel model, String builderVar, String instanceExpr, CommandModel rootModel) {
        // 1. Add nested subcommand classes
        for (CommandModel child : model.getNestedSubcommands()) {
            String childInstanceVar = "this." + getSubcommandFieldName(child);
            String childBuilderVar = "subBuilder_" + sanitizeIdentifier(child.getCommandName());
            spec.addStatement("$T $L = $T.literal($S)",
                    ParameterizedTypeName.get(literalArgumentBuilderClass, commandSourceStackClass),
                    childBuilderVar,
                    commandsClass,
                    child.getCommandName());
            buildBrigadierTree(spec, child, childBuilderVar, childInstanceVar, rootModel);
            spec.addStatement("$L.then($L)", builderVar, childBuilderVar);
        }

        // 2. Add subcommand methods
        for (MethodModel sub : model.getSubcommands()) {
            String subBuilderVar = "subBuilder_" + sanitizeIdentifier(sub.getSubcommandName());
            spec.addStatement("$T $L = $T.literal($S)",
                    ParameterizedTypeName.get(literalArgumentBuilderClass, commandSourceStackClass),
                    subBuilderVar,
                    commandsClass,
                    sub.getSubcommandName());
            buildMethodParametersTree(spec, subBuilderVar, model, sub, instanceExpr, rootModel);
            spec.addStatement("$L.then($L)", builderVar, subBuilderVar);
        }

        // 3. Add default method
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
            ExecutableElement localResolver = findLocalResolver(classModel, p, rootModel);
            if (localResolver != null) {
                List<? extends javax.lang.model.element.VariableElement> resolverParams = localResolver.getParameters();
                int resolverStartIndex = firstParamIsSender(localResolver) ? 1 : 0;
                int resolverWidth = resolverParams.size() - resolverStartIndex;
                for (int i = 0; i < resolverWidth; i++) {
                    javax.lang.model.element.VariableElement rp = resolverParams.get(resolverStartIndex + i);
                    String rpName = rp.getSimpleName().toString();
                    TypeName rpTypeName = TypeName.get(rp.asType());
                    CodeBlock typeBlock = getArgumentTypeExpressionFromTypeName(rpTypeName, rp);
                    boolean rpOptional = rp.getAnnotation(Optional.class) != null;
                    nodes.add(new NodeInfo(rpName, typeBlock, p, i, i == resolverWidth - 1, rpOptional));
                }
            } else {
                int width = getParameterWidth(classModel, p, rootModel);
                CodeBlock typeBlock = getArgumentTypeExpression(classModel, p, rootModel);
                if (width <= 1) {
                    nodes.add(new NodeInfo(p.getName(), typeBlock, p, 0, true, false));
                } else {
                    for (int i = 0; i < width; i++) {
                        nodes.add(new NodeInfo(p.getName() + "_" + i,
                                CodeBlock.of("$T.string()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType")),
                                p, i, i == width - 1, false));
                    }
                }
            }
        }

        buildNodeChainRecursive(spec, parentBuilderVar, classModel, method, instanceExpr, rootModel, nodes, 0);
    }

    private int getParameterWidth(CommandModel classModel, ParameterModel param, CommandModel rootModel) {
        ExecutableElement localRes = findLocalResolver(classModel, param, rootModel);
        if (localRes != null) {
            return getLocalResolverMaxWidth(localRes);
        }
        TypeName typeName = TypeName.get(param.getType());
        if (isPlatformBuiltInType(typeName)) {
            return getPlatformBuiltInWidth(typeName);
        }
        return 1;
    }

    private void buildNodeChainRecursive(MethodSpec.Builder spec, String currentBuilderVar, CommandModel classModel, MethodModel method, String instanceExpr, CommandModel rootModel, List<NodeInfo> nodes, int index) {
        boolean canExecuteHere = false;
        if (index == nodes.size()) {
            canExecuteHere = true;
        } else if (index > 0 && nodes.get(index - 1).isLastForParameter) {
            ParameterModel nextParam = nodes.get(index).parameter;
            if (nextParam.isOptional()) {
                canExecuteHere = true;
            }
        } else if (index > 0 && nodes.get(index).isResolverParamOptional) {
            // Allow execution here when the next node is an @Optional resolver parameter
            canExecuteHere = true;
        }

        if (canExecuteHere) {
            spec.beginControlFlow("$L.executes(ctx ->", currentBuilderVar);
            generateExecutionBlock(spec, classModel, method, instanceExpr, rootModel, nodes, index);
            spec.endControlFlow(")");
        }

        if (index < nodes.size()) {
            NodeInfo node = nodes.get(index);
            String nextBuilderVar = "argBuilder_" + sanitizeIdentifier(method.getMethodName()) + "_" + sanitizeIdentifier(node.nodeName);
            spec.addStatement("$T $L = $T.argument($S, $L)",
                    ParameterizedTypeName.get(requiredArgumentBuilderClass, commandSourceStackClass, WildcardTypeName.subtypeOf(TypeName.get(Object.class))),
                    nextBuilderVar,
                    commandsClass,
                    node.nodeName,
                    node.typeExpression);

            ParameterModel p = node.parameter;
            boolean needsSuggestions = p.getSuggestProvider() != null || TypeName.get(p.getType()).toString().equals("boolean") || TypeName.get(p.getType()).toString().equals("java.lang.Boolean");
            if (needsSuggestions && node.isLastForParameter) {
                int paramIndex = method.getParameters().indexOf(p);
                String helperName = getParameterSuggestionMethodName(classModel, method, paramIndex);
                spec.addStatement("$L.suggests((ctx, sb) -> getSuggestions(ctx, sb, args -> $L(ctx.getSource(), args, sb.getRemaining())))",
                        nextBuilderVar, helperName);
            }

            buildNodeChainRecursive(spec, nextBuilderVar, classModel, method, instanceExpr, rootModel, nodes, index + 1);
            spec.addStatement("$L.then($L)", currentBuilderVar, nextBuilderVar);
        }
    }

    private void generateExecutionBlock(MethodSpec.Builder spec, CommandModel classModel, MethodModel method, String instanceExpr, CommandModel rootModel, List<NodeInfo> nodes, int parsedNodeCount) {
        spec.beginControlFlow("try");

        Permission permission = method.getElement().getAnnotation(Permission.class);
        if (permission != null) {
            spec.beginControlFlow("if (!ctx.getSource().getSender().hasPermission($S))", permission.value());
            String messageKey = permission.message().isEmpty() ? "permission" : permission.message();
            String defaultTemplate = permission.message().isEmpty() ? "You do not have permission to execute this command." : permission.message();
            spec.addStatement("ctx.getSource().getSender().sendMessage($T.text(manager.formatMessage($S, $S, $S), $T.RED))",
                    componentClass, messageKey, defaultTemplate, permission.value(), errorColorClass);
            spec.addStatement("return $T.SINGLE_SUCCESS", commandClass);
            spec.endControlFlow();
        }

        PaperExecutionSource source = new PaperExecutionSource(this, nodes, parsedNodeCount);

        buildMethodExecution(spec, classModel, method, instanceExpr, rootModel, source);

        spec.nextControlFlow("catch ($T e)", Exception.class)
                .addStatement("manager.getErrorHandler().handle(ctx.getSource(), e)")
                .endControlFlow();

        spec.addStatement("return $T.SINGLE_SUCCESS", commandClass);
    }

    private CodeBlock getArgumentTypeExpression(CommandModel classModel, ParameterModel param, CommandModel rootModel) {
        if (findLocalResolver(classModel, param, rootModel) != null) {
            return CodeBlock.of("$T.string()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"));
        }
        return getArgumentTypeExpressionFromTypeName(TypeName.get(param.getType()), param.getElement());
    }

    private CodeBlock getArgumentTypeExpressionFromTypeName(TypeName typeName, javax.lang.model.element.VariableElement element) {
        String name = typeName.toString();

        if (name.equals("int") || name.equals("java.lang.Integer")) {
            return CodeBlock.of("$T.integer()", ClassName.get("com.mojang.brigadier.arguments", "IntegerArgumentType"));
        } else if (name.equals("long") || name.equals("java.lang.Long")) {
            return CodeBlock.of("$T.longArg()", ClassName.get("com.mojang.brigadier.arguments", "LongArgumentType"));
        } else if (name.equals("float") || name.equals("java.lang.Float")) {
            return CodeBlock.of("$T.floatArg()", ClassName.get("com.mojang.brigadier.arguments", "FloatArgumentType"));
        } else if (name.equals("double") || name.equals("java.lang.Double")) {
            return CodeBlock.of("$T.doubleArg()", ClassName.get("com.mojang.brigadier.arguments", "DoubleArgumentType"));
        } else if (name.equals("boolean") || name.equals("java.lang.Boolean")) {
            return CodeBlock.of("$T.bool()", ClassName.get("com.mojang.brigadier.arguments", "BoolArgumentType"));
        } else if (name.equals("java.lang.String")) {
            boolean isGreedy = element.getAnnotation(Greedy.class) != null;
            if (isGreedy) {
                return CodeBlock.of("$T.greedyString()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"));
            } else {
                return CodeBlock.of("$T.string()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"));
            }
        } else if (name.equals("org.bukkit.entity.Player")) {
            return CodeBlock.of("$L.player()", argumentTypesClass);
        } else if (name.equals("org.bukkit.World")) {
            return CodeBlock.of("$L.world()", argumentTypesClass);
        } else if (name.equals("org.bukkit.Location")) {
            return CodeBlock.of("$L.finePosition(true)", argumentTypesClass);
        }

        return CodeBlock.of("$T.string()", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"));
    }

    CodeBlock getArgumentRetrievalExpression(TypeName typeName, String argName) {
        String typeStr = typeName.toString();
        if (typeStr.equals("int") || typeStr.equals("java.lang.Integer")) {
            return CodeBlock.of("$T.getInteger(ctx, $S)", ClassName.get("com.mojang.brigadier.arguments", "IntegerArgumentType"), argName);
        } else if (typeStr.equals("long") || typeStr.equals("java.lang.Long")) {
            return CodeBlock.of("$T.getLong(ctx, $S)", ClassName.get("com.mojang.brigadier.arguments", "LongArgumentType"), argName);
        } else if (typeStr.equals("float") || typeStr.equals("java.lang.Float")) {
            return CodeBlock.of("$T.getFloat(ctx, $S)", ClassName.get("com.mojang.brigadier.arguments", "FloatArgumentType"), argName);
        } else if (typeStr.equals("double") || typeStr.equals("java.lang.Double")) {
            return CodeBlock.of("$T.getDouble(ctx, $S)", ClassName.get("com.mojang.brigadier.arguments", "DoubleArgumentType"), argName);
        } else if (typeStr.equals("boolean") || typeStr.equals("java.lang.Boolean")) {
            return CodeBlock.of("$T.getBool(ctx, $S)", ClassName.get("com.mojang.brigadier.arguments", "BoolArgumentType"), argName);
        } else if (typeStr.equals("java.lang.String")) {
            return CodeBlock.of("$T.getString(ctx, $S)", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), argName);
        } else if (typeStr.equals("org.bukkit.entity.Player")) {
            return CodeBlock.of("ctx.getArgument($S, $T.class).resolve(ctx.getSource()).iterator().next()",
                    argName, ClassName.get("io.papermc.paper.command.brigadier.argument.resolvers.selector", "PlayerSelectorArgumentResolver"));
        } else if (typeStr.equals("org.bukkit.World")) {
            return CodeBlock.of("ctx.getArgument($S, $T.class)", argName, ClassName.get("org.bukkit", "World"));
        } else if (typeStr.equals("org.bukkit.Location")) {
            return CodeBlock.of("ctx.getArgument($S, $T.class).resolve(ctx.getSource()).toLocation(ctx.getSource().getLocation().getWorld())",
                    argName, ClassName.get("io.papermc.paper.command.brigadier.argument.resolvers", "FinePositionResolver"));
        }
        return CodeBlock.of("ctx.getArgument($S, $T.class)", argName, typeName);
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

        NodeInfo(String nodeName, CodeBlock typeExpression, ParameterModel parameter, int resolverArgIndex, boolean isLastForParameter, boolean isResolverParamOptional) {
            this.nodeName = nodeName;
            this.typeExpression = typeExpression;
            this.parameter = parameter;
            this.resolverArgIndex = resolverArgIndex;
            this.isLastForParameter = isLastForParameter;
            this.isResolverParamOptional = isResolverParamOptional;
        }
    }
}
