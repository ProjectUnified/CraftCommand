package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.TypeName;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

/**
 * Interface to generate platform-specific command execution steps.
 */
public interface ExecutionSource {
    /**
     * Generates code to resolve and cast the command sender.
     *
     * @param methodSpec          the target method builder
     * @param classModel          the command class model
     * @param method              the command method model
     * @param rootModel           the root command model
     * @param senderVarName       the variable name to assign the sender to
     * @param senderParam         the sender parameter model
     * @param senderParamTypeName the JavaPoet TypeName of the sender
     */
    void generateSenderResolution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, String senderVarName, ParameterModel senderParam, TypeName senderParamTypeName);

    /**
     * Generates code for platform-specific execution setup or static checks (e.g. checking command length).
     *
     * @param methodSpec the target method builder
     * @param classModel the command class model
     * @param method     the command method model
     * @param rootModel  the root command model
     */
    void generateExecutionSetup(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel);

    /**
     * Generates code to parse and resolve a specific command parameter.
     *
     * @param methodSpec the target method builder
     * @param classModel the command class model
     * @param method     the command method model
     * @param rootModel  the root command model
     * @param p          the parameter model to resolve
     * @param varName    the variable name to assign the resolved parameter to
     * @param senderVar  the variable name of the resolved sender
     * @param paramIndex the index of the parameter in the command method (excluding sender)
     */
    void generateParameterResolution(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel, ParameterModel p, String varName, String senderVar, int paramIndex);
}
