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
import javax.lang.model.element.VariableElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Brigadier-based implementation of {@link ExecutionSource} for Paper command execution.
 */
public class PaperExecutionSource implements ExecutionSource {
    private final PaperCommandProcessor processor;
    private final List<PaperCommandProcessor.NodeInfo> nodes;
    private final int parsedNodeCount;

    PaperExecutionSource(PaperCommandProcessor processor, List<PaperCommandProcessor.NodeInfo> nodes, int parsedNodeCount) {
        this.processor = processor;
        this.nodes = nodes;
        this.parsedNodeCount = parsedNodeCount;
    }

    @Override
    public void generateSenderResolution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, String senderVarName, ParameterModel senderParam, TypeName senderParamTypeName) {
        if (processor.isSenderBaseType(senderParamTypeName)) {
            methodSpec.addStatement("$T $L = ctx.getSource()", processor.commandSourceStackClass, senderVarName);
        } else if (senderParam.getElement().getAnnotation(io.github.projectunified.craftcommand.annotation.Resolve.class) != null) {
            ExecutableElement localResolver = processor.findLocalResolver(classModel, senderParam, rootModel);
            if (localResolver != null) {
                List<String> argNames = resolveResolverParams(methodSpec, localResolver, senderVarName, 0);
                processor.generateResolverInvocation(methodSpec, localResolver, classModel, rootModel, senderParamTypeName, senderVarName, "ctx.getSource()", argNames, true);
            } else {
                methodSpec.addStatement("$T $L = null", senderParamTypeName, senderVarName);
            }
        } else {
            String castMethodName = "as" + BaseCommandProcessor.getSimpleName(senderParamTypeName);
            methodSpec.addStatement("$T $L = $L(ctx.getSource())", senderParamTypeName, senderVarName, castMethodName);
        }
    }

    @Override
    public void generateExecutionSetup(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel) {
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
                List<String> argNames = resolveResolverParamsWithDefaults(methodSpec, localResolver, varName, paramIndex);
                String resolverSenderExpr = processor.getResolverSenderExpression(localResolver, "ctx.getSource()", senderVarName, TypeName.get(method.getSenderType()));
                boolean includeSender = processor.isSenderParam(TypeName.get(localResolver.getParameters().get(0).asType()), method);
                processor.generateResolverInvocation(methodSpec, localResolver, classModel, rootModel, pmTypeName, varName, resolverSenderExpr, argNames, includeSender);
            } else if (processor.isBuiltInType(pmTypeName)) {
                String defVal = pmTypeName.isPrimitive() ? processor.getDefaultPrimitiveValue(pm.getType()) : "null";
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName, defVal);
            } else {
                String strVar = varName + "_str";
                methodSpec.addStatement("String $L = $T.getString(ctx, $S)", strVar,
                        ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.getName());
                methodSpec.addStatement("$T $L = ($T) manager.getResolver($T.class).resolve($L, new String[]{$L}, $L)",
                        pmTypeName, varName, pmTypeName,
                        pmTypeName.isPrimitive() ? pmTypeName.box() : pmTypeName,
                        "ctx.getSource()", strVar, strVar);
            }
        } else {
            if (localResolver != null) {
                List<String> argNames = resolveResolverParamsFromBrigadier(methodSpec, localResolver, varName, paramIndex, method);
                String resolverSenderExpr = processor.getResolverSenderExpression(localResolver, "ctx.getSource()", senderVarName, TypeName.get(method.getSenderType()));
                boolean includeSender = processor.isSenderParam(TypeName.get(localResolver.getParameters().get(0).asType()), method);
                processor.generateResolverInvocation(methodSpec, localResolver, classModel, rootModel, pmTypeName, varName, resolverSenderExpr, argNames, includeSender);
            } else if (pm.isGreedy() && !pmTypeName.toString().equals("java.lang.String")) {
                methodSpec.addStatement("$T $L = $T.getString(ctx, $S)", String.class, varName + "_greedy", ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.getName());
                methodSpec.addStatement("$T $L", pmTypeName, varName);
                processor.resolveParameterForType(methodSpec, pmTypeName, varName, varName + "_greedy");
            } else if (processor.isBuiltInType(pmTypeName)) {
                CodeBlock retrievalExpr = processor.getArgumentRetrievalExpression(pmTypeName, pm.getName());
                methodSpec.addStatement("$T $L = $L", pmTypeName, varName, retrievalExpr);
            } else {
                String strVar = varName + "_str";
                methodSpec.addStatement("String $L = $T.getString(ctx, $S)", strVar,
                        ClassName.get("com.mojang.brigadier.arguments", "StringArgumentType"), pm.getName());
                methodSpec.addStatement("$T $L = ($T) manager.getResolver($T.class).resolve($L, new String[]{$L}, $L)",
                        pmTypeName, varName, pmTypeName,
                        pmTypeName.isPrimitive() ? pmTypeName.box() : pmTypeName,
                        "ctx.getSource()", strVar, strVar);
            }
        }
    }

    private List<String> resolveResolverParamsWithDefaults(MethodSpec.Builder methodSpec, ExecutableElement localResolver, String varName, int paramIndex) {
        List<? extends VariableElement> resolverParams = localResolver.getParameters();
        int startIndex = processor.firstParamIsSender(localResolver) ? 1 : 0;
        List<String> argNames = new ArrayList<>();
        for (int j = startIndex; j < resolverParams.size(); j++) {
            VariableElement rp = resolverParams.get(j);
            TypeName rpTypeName = TypeName.get(rp.asType());
            String rpVarName = varName + "_rp_" + (j - startIndex);
            argNames.add(rpVarName);
            if (processor.isSenderParam(rpTypeName, null)) {
                String castMethodName = "as" + BaseCommandProcessor.getSimpleName(rpTypeName);
                methodSpec.addStatement("$T $L = $L($L)", rpTypeName, rpVarName, castMethodName, "ctx.getSource()");
            } else {
                Default defaultAnn = rp.getAnnotation(Default.class);
                String defaultValue = (defaultAnn != null && !defaultAnn.value().isEmpty()) ? defaultAnn.value() : null;
                methodSpec.addStatement("$T $L", rpTypeName, rpVarName);
                if (defaultValue != null && !defaultValue.isEmpty()) {
                    methodSpec.addStatement("$L = $L", rpVarName, processor.getAssignmentValueForType(rpTypeName, defaultValue));
                } else {
                    methodSpec.addStatement("$L = $L", rpVarName, rpTypeName.isPrimitive() ? processor.getDefaultPrimitiveValue(rp.asType()) : "null");
                }
            }
        }
        return argNames;
    }

    private List<String> resolveResolverParamsFromBrigadier(MethodSpec.Builder methodSpec, ExecutableElement localResolver, String varName, int paramIndex, MethodModel method) {
        List<? extends VariableElement> resolverParams = localResolver.getParameters();
        int startIndex = processor.firstParamIsSender(localResolver) ? 1 : 0;
        List<String> argNames = new ArrayList<>();
        for (int j = startIndex; j < resolverParams.size(); j++) {
            VariableElement rp = resolverParams.get(j);
            TypeName rpTypeName = TypeName.get(rp.asType());
            if (processor.isSenderParam(rpTypeName, method)) continue;
            String rpVarName = varName + "_rp_" + (j - startIndex);
            argNames.add(rpVarName);
            methodSpec.addStatement("$T $L", rpTypeName, rpVarName);
            CodeBlock retrievalExpr = processor.getArgumentRetrievalExpression(rpTypeName, rp.getSimpleName().toString());
            methodSpec.addStatement("$L = $L", rpVarName, retrievalExpr);
        }
        return argNames;
    }

    private List<String> resolveResolverParams(MethodSpec.Builder methodSpec, ExecutableElement localResolver, String varName, int paramIndex) {
        List<? extends VariableElement> resolverParams = localResolver.getParameters();
        int startIndex = processor.firstParamIsSender(localResolver) ? 1 : 0;
        List<String> argNames = new ArrayList<>();
        for (int j = startIndex; j < resolverParams.size(); j++) {
            VariableElement rp = resolverParams.get(j);
            TypeName rpTypeName = TypeName.get(rp.asType());
            String rpVarName = varName + "_rp_" + (j - startIndex);
            argNames.add(rpVarName);
            methodSpec.addStatement("$T $L = null", rpTypeName, rpVarName);
        }
        return argNames;
    }
}
