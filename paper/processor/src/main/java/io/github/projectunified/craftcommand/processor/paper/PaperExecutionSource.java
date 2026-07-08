package io.github.projectunified.craftcommand.processor.paper;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import io.github.projectunified.craftcommand.annotation.Default;
import io.github.projectunified.craftcommand.processor.BaseCommandProcessor;
import io.github.projectunified.craftcommand.processor.ExecutionSource;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.lang.model.element.ExecutableElement;
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
                String resolverInstanceExpr = processor.getResolverInstanceExpr(localResolver, classModel, rootModel);
                if (resolverInstanceExpr == null) {
                    // Resolver is on an outer class not accessible from this wrapper.
                    methodSpec.addStatement("$T $L = $L", pmTypeName, varName, pmTypeName.isPrimitive() ? "false" : "null");
                } else {
                    List<? extends javax.lang.model.element.VariableElement> resolverParams = localResolver.getParameters();
                    int resolverStartIndex = processor.firstParamIsSender(localResolver) ? 1 : 0;
                    List<String> resolverArgNames = new ArrayList<>();
                    for (int j = resolverStartIndex; j < resolverParams.size(); j++) {
                        javax.lang.model.element.VariableElement rp = resolverParams.get(j);
                        TypeName rpTypeName = TypeName.get(rp.asType());
                        String rpVarName = varName + "_rp_" + (j - resolverStartIndex);
                        resolverArgNames.add(rpVarName);
                        Default defaultAnn = rp.getAnnotation(Default.class);
                        boolean isOptional = defaultAnn != null;
                        String defaultValue = (isOptional && !defaultAnn.value().isEmpty()) ? defaultAnn.value() : null;
                        methodSpec.addStatement("$T $L", rpTypeName, rpVarName);
                        if (defaultValue != null && !defaultValue.isEmpty()) {
                            methodSpec.addStatement("$L = $L", rpVarName, processor.getAssignmentValueForType(rpTypeName, defaultValue));
                        } else {
                            methodSpec.addStatement("$L = $L", rpVarName, rpTypeName.isPrimitive() ? processor.getDefaultPrimitiveValue(rp.asType()) : "null");
                        }
                    }
                    CodeBlock.Builder resolveCallBuilder1 = CodeBlock.builder().add("$L.$L(", resolverInstanceExpr, localResolver.getSimpleName());
                    if (resolverStartIndex == 1) {
                        resolveCallBuilder1.add("$L", senderVarName);
                        if (!resolverArgNames.isEmpty()) {
                            resolveCallBuilder1.add(", ");
                        }
                    }
                    for (int j = 0; j < resolverArgNames.size(); j++) {
                        if (j > 0) resolveCallBuilder1.add(", ");
                        resolveCallBuilder1.add("$L", resolverArgNames.get(j));
                    }
                    resolveCallBuilder1.add(")");
                    methodSpec.addStatement("$T $L = $L", pmTypeName, varName, resolveCallBuilder1.build());
                }
            } else if (processor.isBuiltInType(pmTypeName)) {
                String defVal = pmTypeName.isPrimitive() ? processor.getDefaultPrimitiveValue(pm.getType()) : "null";
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName, defVal);
            } else {
                methodSpec.addStatement("$T $L = manager.resolveParameter(ctx.getSource(), $T.class, new String[0], new int[]{0}, $S, true, null)",
                        pmTypeName, varName, pmTypeName.isPrimitive() ? pmTypeName.box() : pmTypeName, pm.getName());
            }
        } else {
            if (localResolver != null) {
                String resolverInstanceExpr2 = processor.getResolverInstanceExpr(localResolver, classModel, rootModel);
                if (resolverInstanceExpr2 == null) {
                    // Resolver is on an outer class not accessible from this wrapper.
                    methodSpec.addStatement("$T $L = $L", pmTypeName, varName, pmTypeName.isPrimitive() ? "false" : "null");
                } else {
                    List<? extends javax.lang.model.element.VariableElement> resolverParams = localResolver.getParameters();
                    int resolverStartIndex = processor.firstParamIsSender(localResolver) ? 1 : 0;
                    List<String> resolverArgNames = new ArrayList<>();
                    for (int j = resolverStartIndex; j < resolverParams.size(); j++) {
                        javax.lang.model.element.VariableElement rp = resolverParams.get(j);
                        TypeName rpTypeName = TypeName.get(rp.asType());
                        String rpVarName = varName + "_rp_" + (j - resolverStartIndex);
                        resolverArgNames.add(rpVarName);
                        Default defaultAnn = rp.getAnnotation(Default.class);
                        boolean isOptional = defaultAnn != null;
                        String defaultValue = (isOptional && !defaultAnn.value().isEmpty()) ? defaultAnn.value() : null;
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
                    CodeBlock.Builder resolveCallBuilder2 = CodeBlock.builder().add("$L.$L(", resolverInstanceExpr2, localResolver.getSimpleName());
                    if (resolverStartIndex == 1) {
                        resolveCallBuilder2.add("$L", senderVarName);
                        if (!resolverArgNames.isEmpty()) {
                            resolveCallBuilder2.add(", ");
                        }
                    }
                    for (int j = 0; j < resolverArgNames.size(); j++) {
                        if (j > 0) resolveCallBuilder2.add(", ");
                        resolveCallBuilder2.add("$L", resolverArgNames.get(j));
                    }
                    resolveCallBuilder2.add(")");
                    methodSpec.addStatement("$T $L = $L", pmTypeName, varName, resolveCallBuilder2.build());
                }
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
