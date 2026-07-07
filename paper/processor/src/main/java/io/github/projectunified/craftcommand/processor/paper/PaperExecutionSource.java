package io.github.projectunified.craftcommand.processor.paper;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import io.github.projectunified.craftcommand.annotation.Optional;
import io.github.projectunified.craftcommand.processor.BaseCommandProcessor;
import io.github.projectunified.craftcommand.processor.ExecutionSource;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Brigadier-based implementation of {@link ExecutionSource} for Paper command execution.
 */
public class PaperExecutionSource implements ExecutionSource {
    private final PaperCommandProcessor processor;
    private final List<PaperCommandProcessor.NodeInfo> nodes;
    private final int parsedNodeCount;

    /**
     * Constructs a PaperExecutionSource.
     *
     * @param processor       the paper command processor instance
     * @param nodes           the Brigadier nodes list
     * @param parsedNodeCount the number of nodes parsed up to this execution point
     */
    PaperExecutionSource(PaperCommandProcessor processor, List<PaperCommandProcessor.NodeInfo> nodes, int parsedNodeCount) {
        this.processor = processor;
        this.nodes = nodes;
        this.parsedNodeCount = parsedNodeCount;
    }

    @Override
    public void generateSenderResolution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, String senderVarName, ParameterModel senderParam, TypeName senderParamTypeName) {
        methodSpec.addComment("Resolve sender");
        if (processor.isSenderBaseType(senderParamTypeName)) {
            methodSpec.addStatement("$T $L = ctx.getSource()", processor.commandSourceStackClass, senderVarName);
        } else {
            String castMethodName = "as" + BaseCommandProcessor.getSimpleName(senderParamTypeName);
            methodSpec.addStatement("$T $L = $L(ctx.getSource())", senderParamTypeName, senderVarName, castMethodName);
        }
    }

    @Override
    public void generateExecutionSetup(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel) {
        // No setup required for Brigadier
    }

    @Override
    public void generateParameterResolution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, ParameterModel pm, String varName, String senderVarName, int paramIndex) {
        TypeName pmTypeName = TypeName.get(pm.getType());

        List<PaperCommandProcessor.NodeInfo> parsedSegments = new ArrayList<>();
        for (int i = 0; i < parsedNodeCount; i++) {
            if (nodes.get(i).parameter == pm) {
                parsedSegments.add(nodes.get(i));
            }
        }

        ExecutableElement localResolver = processor.findLocalResolver(classModel, pm, rootModel);

        if (parsedSegments.isEmpty()) {
            methodSpec.addComment("Optional parameter '" + pm.getName() + "' not provided");
            if (localResolver != null) {
                List<? extends javax.lang.model.element.VariableElement> resolverParams = localResolver.getParameters();
                int resolverStartIndex = processor.firstParamIsSender(localResolver) ? 1 : 0;
                List<String> resolverArgNames = new ArrayList<>();
                for (int j = resolverStartIndex; j < resolverParams.size(); j++) {
                    javax.lang.model.element.VariableElement rp = resolverParams.get(j);
                    TypeName rpTypeName = TypeName.get(rp.asType());
                    String rpVarName = varName + "_rp_" + (j - resolverStartIndex);
                    resolverArgNames.add(rpVarName);
                    Optional optionalAnn = rp.getAnnotation(Optional.class);
                    boolean isOptional = optionalAnn != null;
                    String defaultValue = isOptional ? optionalAnn.value() : null;
                    methodSpec.addStatement("$T $L", rpTypeName, rpVarName);
                    if (defaultValue != null && !defaultValue.isEmpty()) {
                        methodSpec.addStatement("$L = $L", rpVarName, processor.getAssignmentValueForType(rpTypeName, defaultValue));
                    } else {
                        methodSpec.addStatement("$L = $L", rpVarName, rpTypeName.isPrimitive() ? BaseCommandProcessor.getDefaultPrimitiveValue(rp.asType()) : "null");
                    }
                }
                String resolverInstanceExpr = processor.getInstanceVarExpression(processor.findModelForClass(rootModel, (TypeElement) localResolver.getEnclosingElement()), rootModel);
                StringBuilder resolveCall = new StringBuilder(resolverInstanceExpr).append(".").append(localResolver.getSimpleName()).append("(");
                if (resolverStartIndex == 1) {
                    resolveCall.append(senderVarName);
                    if (!resolverArgNames.isEmpty()) {
                        resolveCall.append(", ");
                    }
                }
                for (int j = 0; j < resolverArgNames.size(); j++) {
                    resolveCall.append(resolverArgNames.get(j));
                    if (j < resolverArgNames.size() - 1) {
                        resolveCall.append(", ");
                    }
                }
                resolveCall.append(")");
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName, resolveCall.toString());
            } else if (processor.isBuiltInType(pmTypeName)) {
                String defVal = pmTypeName.isPrimitive() ? BaseCommandProcessor.getDefaultPrimitiveValue(pm.getType()) : "null";
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName, defVal);
            } else {
                String resolverMethod = processor.getResolverMethodName(pmTypeName);
                methodSpec.addStatement("$T $L = $L($L, new String[0], $S, true, null)",
                        pmTypeName, varName, resolverMethod, senderVarName, pm.getName());
            }
        } else {
            if (localResolver != null) {
                methodSpec.addComment("Resolve parameter '" + pm.getName() + "' using local resolver " + localResolver.getSimpleName());
                List<? extends javax.lang.model.element.VariableElement> resolverParams = localResolver.getParameters();
                int resolverStartIndex = processor.firstParamIsSender(localResolver) ? 1 : 0;
                List<String> resolverArgNames = new ArrayList<>();
                for (int j = resolverStartIndex; j < resolverParams.size(); j++) {
                    javax.lang.model.element.VariableElement rp = resolverParams.get(j);
                    TypeName rpTypeName = TypeName.get(rp.asType());
                    String rpVarName = varName + "_rp_" + (j - resolverStartIndex);
                    resolverArgNames.add(rpVarName);
                    Optional optionalAnn = rp.getAnnotation(Optional.class);
                    boolean isOptional = optionalAnn != null;
                    String defaultValue = isOptional ? optionalAnn.value() : null;
                    methodSpec.addStatement("$T $L", rpTypeName, rpVarName);
                    CodeBlock retrievalExpr = processor.getArgumentRetrievalExpression(rpTypeName, rp.getSimpleName().toString());
                    if (isOptional) {
                        methodSpec.beginControlFlow("try");
                        methodSpec.addStatement("$L = $L", rpVarName, retrievalExpr);
                        methodSpec.nextControlFlow("catch ($T e)", IllegalArgumentException.class);
                        if (defaultValue != null && !defaultValue.isEmpty()) {
                            methodSpec.addStatement("$L = $L", rpVarName, processor.getAssignmentValueForType(rpTypeName, defaultValue));
                        } else {
                            methodSpec.addStatement("$L = $L", rpVarName, rpTypeName.isPrimitive() ? BaseCommandProcessor.getDefaultPrimitiveValue(rp.asType()) : "null");
                        }
                        methodSpec.endControlFlow();
                    } else {
                        methodSpec.addStatement("$L = $L", rpVarName, retrievalExpr);
                    }
                }
                String resolverInstanceExpr = processor.getInstanceVarExpression(processor.findModelForClass(rootModel, (TypeElement) localResolver.getEnclosingElement()), rootModel);
                StringBuilder resolveCall = new StringBuilder(resolverInstanceExpr).append(".").append(localResolver.getSimpleName()).append("(");
                if (resolverStartIndex == 1) {
                    resolveCall.append(senderVarName);
                    if (!resolverArgNames.isEmpty()) {
                        resolveCall.append(", ");
                    }
                }
                for (int j = 0; j < resolverArgNames.size(); j++) {
                    resolveCall.append(resolverArgNames.get(j));
                    if (j < resolverArgNames.size() - 1) {
                        resolveCall.append(", ");
                    }
                }
                resolveCall.append(")");
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName, resolveCall.toString());
            } else {
                methodSpec.addComment("Resolve parameter '" + pm.getName() + "'");
                String typeStr = pmTypeName.toString();
                if (typeStr.equals("int") || typeStr.equals("java.lang.Integer")) {
                    methodSpec.addStatement("$T $L = $T.getInteger(ctx, $S)", pmTypeName, varName, ClassName.get("com.mojang.brigadier.arguments", "IntegerArgumentType"), pm.getName());
                } else if (typeStr.equals("long") || typeStr.equals("java.lang.Long")) {
                    methodSpec.addStatement("$T $L = $T.getLong(ctx, $S)", pmTypeName, varName, ClassName.get("com.mojang.brigadier.arguments", "LongArgumentType"), pm.getName());
                } else if (typeStr.equals("float") || typeStr.equals("java.lang.Float")) {
                    methodSpec.addStatement("$T $L = $T.getFloat(ctx, $S)", pmTypeName, varName, ClassName.get("com.mojang.brigadier.arguments", "FloatArgumentType"), pm.getName());
                } else if (typeStr.equals("double") || typeStr.equals("java.lang.Double")) {
                    methodSpec.addStatement("$T $L = $T.getDouble(ctx, $S)", pmTypeName, varName, ClassName.get("com.mojang.brigadier.arguments", "DoubleArgumentType"), pm.getName());
                } else if (typeStr.equals("boolean") || typeStr.equals("java.lang.Boolean")) {
                    methodSpec.addStatement("$T $L = $T.getBool(ctx, $S)", pmTypeName, varName, ClassName.get("com.mojang.brigadier.arguments", "BoolArgumentType"), pm.getName());
                } else if (typeStr.equals("java.lang.String")) {
                    methodSpec.addStatement("$T $L = $T.getString(ctx, $S)", pmTypeName, varName, ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.getName());
                } else if (typeStr.equals("org.bukkit.entity.Player")) {
                    methodSpec.addStatement("$T $L = ctx.getArgument($S, $T.class).resolve(ctx.getSource()).iterator().next()",
                            pmTypeName, varName, pm.getName(), ClassName.get("io.papermc.paper.command.brigadier.argument.resolvers.selector", "PlayerSelectorArgumentResolver"));
                } else if (typeStr.equals("org.bukkit.World")) {
                    methodSpec.addStatement("$T $L = ctx.getArgument($S, $T.class)", pmTypeName, varName, pm.getName(), ClassName.get("org.bukkit", "World"));
                } else if (typeStr.equals("org.bukkit.Location")) {
                    methodSpec.addStatement("$T $L = ctx.getArgument($S, $T.class).resolve(ctx.getSource()).toLocation(ctx.getSource().getLocation().getWorld())",
                            pmTypeName, varName, pm.getName(), ClassName.get("io.papermc.paper.command.brigadier.argument.resolvers", "FinePositionResolver"));
                } else {
                    String[] subArgExprs = new String[parsedSegments.size()];
                    for (int i = 0; i < parsedSegments.size(); i++) {
                        subArgExprs[i] = "ctx.getArgument(\"" + parsedSegments.get(i).nodeName + "\", String.class)";
                    }
                    String subArgsArray = "new String[]{" + String.join(", ", subArgExprs) + "}";
                    String resolverMethod = processor.getResolverMethodName(pmTypeName);
                    methodSpec.addStatement("$T $L = $L($L, $L, $S, false, null)",
                            pmTypeName, varName, resolverMethod, senderVarName, subArgsArray, pm.getName());
                }
            }
        }
    }
}
