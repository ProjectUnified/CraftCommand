package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.MethodSpec;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

/**
 * One generated command method body: the method being written plus the model and the generated locals it
 * refers to.
 *
 * <p>Carrying this state together keeps the code generators to a model in, statements out shape instead of long
 * parameter lists.
 */
final class MethodSlot {
    private final MethodSpec.Builder methodSpec;
    private final CommandModel classModel;
    private final MethodModel method;
    private final CommandModel rootModel;
    private final String instanceVar;
    private final String senderVarName;
    private final LocalNames locals = new LocalNames();

    /**
     * @param methodSpec    the method being generated
     * @param classModel    the command class whose method is generated
     * @param method        the command method being generated
     * @param rootModel     the root command model, used to reach instances of enclosing classes
     * @param instanceVar   the expression referring to the command instance
     * @param senderVarName the local holding the command sender
     */
    MethodSlot(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel,
               String instanceVar, String senderVarName) {
        this.methodSpec = methodSpec;
        this.classModel = classModel;
        this.method = method;
        this.rootModel = rootModel;
        this.instanceVar = instanceVar;
        this.senderVarName = senderVarName;
    }

    MethodSpec.Builder methodSpec() {
        return methodSpec;
    }

    CommandModel classModel() {
        return classModel;
    }

    MethodModel method() {
        return method;
    }

    CommandModel rootModel() {
        return rootModel;
    }

    String instanceVar() {
        return instanceVar;
    }

    String senderVarName() {
        return senderVarName;
    }

    LocalNames locals() {
        return locals;
    }

    /**
     * Allocates the local for a parameter, named after the parameter the user declared.
     *
     * @param parameter the parameter to name
     * @param fallback  the name to use when the declared name is unusable
     * @return the local variable name
     */
    String localFor(ParameterModel parameter, String fallback) {
        return locals.allocate(declaredName(parameter), fallback);
    }

    /**
     * @return the identifier the user declared for a parameter, or {@code null} when unavailable
     */
    static String declaredName(ParameterModel parameter) {
        return LocalNames.declaredName(parameter);
    }
}
