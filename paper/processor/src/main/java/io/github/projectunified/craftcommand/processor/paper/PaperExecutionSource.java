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
                        methodSpec.addStatement("$L = $L", rpVarName, rpTypeName.isPrimitive() ? processor.getDefaultPrimitiveValue(rp.asType()) : "null");
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
                String defVal = pmTypeName.isPrimitive() ? processor.getDefaultPrimitiveValue(pm.getType()) : "null";
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName, defVal);
            } else {
                methodSpec.addStatement("$T $L = manager.resolveParameter(ctx.getSource(), $T.class, new String[0], new int[]{0}, $S, true, null)",
                        pmTypeName, varName, pmTypeName.isPrimitive() ? pmTypeName.box() : pmTypeName, pm.getName());
            }
        } else {
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
                    CodeBlock retrievalExpr = processor.getArgumentRetrievalExpression(rpTypeName, rp.getSimpleName().toString());
                    if (isOptional) {
                        methodSpec.beginControlFlow("try");
                        methodSpec.addStatement("$L = $L", rpVarName, retrievalExpr);
                        methodSpec.nextControlFlow("catch ($T e)", IllegalArgumentException.class);
                        if (defaultValue != null && !defaultValue.isEmpty()) {
                            methodSpec.addStatement("$L = $L", rpVarName, processor.getAssignmentValueForType(rpTypeName, defaultValue));
                        } else {
                            methodSpec.addStatement("$L = $L", rpVarName, rpTypeName.isPrimitive() ? processor.getDefaultPrimitiveValue(rp.asType()) : "null");
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
                if (pm.isGreedy() && !pmTypeName.toString().equals("java.lang.String")) {
                    // Greedy non-String: get string from Brigadier, then parse
                    methodSpec.addStatement("$T $L = $T.getString(ctx, $S)", String.class, varName + "_greedy", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.getName());
                    methodSpec.addStatement("$T $L", pmTypeName, varName);
                    processor.resolveParameterForType(methodSpec, pmTypeName, varName, varName + "_greedy");
                } else {
                    CodeBlock retrievalExpr = processor.getArgumentRetrievalExpression(pmTypeName, pm.getName());
                    methodSpec.addStatement("$T $L = $L", pmTypeName, varName, retrievalExpr);
                }
            }
        }
    }
}
