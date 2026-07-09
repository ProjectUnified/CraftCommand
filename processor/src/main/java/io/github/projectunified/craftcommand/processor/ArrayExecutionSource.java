package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import io.github.projectunified.craftcommand.annotation.Resolve;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.lang.model.element.ExecutableElement;
import java.util.ArrayList;
import java.util.List;

/**
 * Array-based implementation of {@link ExecutionSource} for Bukkit/Standalone command execution.
 */
public class ArrayExecutionSource implements ExecutionSource {
    private final BaseCommandProcessor processor;
    private final String argsVar;
    private final boolean hasDynamic;
    private final String argIdxVar;

    /**
     * Constructs an ArrayExecutionSource.
     *
     * @param processor  the base command processor instance
     * @param argsVar    the variable name of the arguments array
     * @param hasDynamic whether dynamic (global resolver) parameter resolution is required
     * @param argIdxVar  the index pointer variable name
     */
    public ArrayExecutionSource(BaseCommandProcessor processor, String argsVar, boolean hasDynamic, String argIdxVar) {
        this.processor = processor;
        this.argsVar = argsVar;
        this.hasDynamic = hasDynamic;
        this.argIdxVar = argIdxVar;
    }

    @Override
    public void generateSenderResolution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, String senderVarName, ParameterModel senderParam, TypeName senderParamTypeName) {
        processor.buildSenderResolution(methodSpec, classModel, method, rootModel, senderVarName, senderParam, senderParamTypeName);
    }

    @Override
    public void generateExecutionSetup(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel) {
        if (method.getParameters().isEmpty()) {
            return;
        }

        int staticRequiredCount = 0;
        for (ParameterModel p : method.getParameters()) {
            if (!p.isOptional()) {
                ExecutableElement localRes = processor.findLocalResolver(classModel, p, rootModel);
                if (localRes != null) {
                    staticRequiredCount += processor.getLocalResolverMinWidth(localRes);
                } else {
                    staticRequiredCount += 1;
                }
            }
        }
        if (staticRequiredCount > 0) {
            methodSpec.beginControlFlow("if ($L.length < $L)", argsVar, staticRequiredCount)
                    .addStatement("throw new $T(manager.formatMessage($S, $S, $S))",
                            CommandException.class,
                            "usage",
                            "Usage: %s",
                            BaseCommandProcessor.getUsage(method))
                    .endControlFlow();
        }

        if (hasDynamic) {
            methodSpec.addStatement("int[] argIdxHolder = { 0 }");
        } else {
            methodSpec.addStatement("int argIdx = 0");
        }
    }

    @Override
    public void generateParameterResolution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, ParameterModel p, String varName, String senderVarName, int paramIndex) {
        // Check if param has @Resolve — use unified recursive approach
        Resolve resolveAnn = p.getElement().getAnnotation(Resolve.class);
        if (resolveAnn != null && classModel.getResolverMethod(resolveAnn.value()) != null) {
            generateResolverResolution(methodSpec, classModel, method, rootModel, classModel.getResolverMethod(resolveAnn.value()), varName, senderVarName);
            return;
        }

        ExecutableElement localResolver = processor.findLocalResolver(classModel, p, rootModel);
        TypeName pTypeName = TypeName.get(p.getType());

        if (localResolver != null) {
            processor.buildLocalResolverParameter(methodSpec, classModel, method, p, pTypeName, varName, localResolver, rootModel, senderVarName, argsVar, argIdxVar, hasDynamic, paramIndex);
        } else if (p.isGreedy() && pTypeName.toString().endsWith("[]")) {
            processor.buildBuiltInParameter(methodSpec, p, pTypeName, varName, argsVar, argIdxVar, senderVarName, hasDynamic, paramIndex);
        } else if (processor.isBuiltInType(pTypeName)) {
            processor.buildBuiltInParameter(methodSpec, p, pTypeName, varName, argsVar, argIdxVar, senderVarName, hasDynamic, paramIndex);
        } else {
            // Non-built-in type: only reached when hasDynamic=true, so argIdxHolder is always declared
            methodSpec.addStatement("$T $L", pTypeName, varName);
            String defValLiteral = p.getDefaultValue() == null ? "null" : com.palantir.javapoet.CodeBlock.of("$S", p.getDefaultValue()).toString();
            methodSpec.addStatement("$L = manager.resolveParameter(sender, $T.class, $L, $L, $S, $L, $L)",
                    varName, pTypeName.isPrimitive() ? pTypeName.box() : pTypeName,
                    argsVar, "argIdxHolder", p.getName(), p.isOptional(), defValLiteral);
        }
    }

    /**
     * Unified resolver param resolution: resolves each non-sender param using the same code path,
     * then invokes the resolver method. Supports @Default, @Greedy, @Name, @Suggest, @Resolve (nested).
     */
    private void generateResolverResolution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, MethodModel resolverModel, String varName, String senderVarName) {
        ExecutableElement resolverElement = resolverModel.getElement();
        TypeName returnType = TypeName.get(resolverModel.getElement().getReturnType());

        // Determine if we should include sender
        boolean includeSender = false;
        if (!resolverModel.getParameters().isEmpty()) {
            includeSender = processor.isSenderParam(TypeName.get(resolverModel.getParameters().get(0).getType()), method);
        }

        // Resolve each non-sender resolver param (recursive — same code path)
        List<String> argNames = new ArrayList<>();
        for (int i = 0; i < resolverModel.getParameters().size(); i++) {
            ParameterModel rp = resolverModel.getParameters().get(i);
            if (processor.isSenderParam(TypeName.get(rp.getType()), method)) continue;
            String rpVarName = varName + "_rp_" + i;
            argNames.add(rpVarName);
            generateParameterResolution(methodSpec, classModel, method, rootModel, rp, rpVarName, senderVarName, i);
        }

        // Invoke resolver method
        String resolverSenderExpr = processor.getResolverSenderExpression(resolverElement, method.getSenderParameter().getName(), senderVarName, TypeName.get(method.getSenderType()));
        processor.generateResolverInvocation(methodSpec, resolverElement, classModel, rootModel, returnType, varName, resolverSenderExpr, argNames, includeSender);
    }
}
