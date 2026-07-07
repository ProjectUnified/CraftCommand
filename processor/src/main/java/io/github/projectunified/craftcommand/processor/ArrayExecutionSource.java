package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.lang.model.element.ExecutableElement;

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
            methodSpec.addComment("Verify that there are at least " + staticRequiredCount + " arguments provided to satisfy required parameters");
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
        ExecutableElement localResolver = processor.findLocalResolver(classModel, p, rootModel);
        TypeName pTypeName = TypeName.get(p.getType());

        if (localResolver != null) {
            processor.buildLocalResolverParameter(methodSpec, classModel, p, pTypeName, varName, localResolver, rootModel, senderVarName, argsVar, argIdxVar, hasDynamic, paramIndex);
        } else {
            if (processor.isBuiltInType(pTypeName)) {
                processor.buildBuiltInParameter(methodSpec, p, pTypeName, varName, argsVar, argIdxVar, senderVarName, hasDynamic, paramIndex);
            } else {
                methodSpec.addComment("Parse parameter '" + p.getName() + "' of type " + pTypeName.toString() + " using manager's resolveParameter");
                methodSpec.addStatement("$T $L", pTypeName, varName);
                String defValLiteral = p.getDefaultValue() == null ? "null" : CodeBlock.of("$S", p.getDefaultValue()).toString();
                methodSpec.addStatement("$L = manager.resolveParameter(senderCast, $T.class, $L, argIdxHolder, $S, $L, $L)",
                        varName, pTypeName.isPrimitive() ? pTypeName.box() : pTypeName,
                        argsVar, p.getName(), p.isOptional(), defValLiteral);
            }
        }
    }
}
