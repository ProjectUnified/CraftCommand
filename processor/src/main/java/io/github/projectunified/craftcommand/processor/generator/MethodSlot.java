package io.github.projectunified.craftcommand.processor.generator;

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
record MethodSlot(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method,
                  CommandModel rootModel, String senderVarName, LocalNames locals) {
    MethodSlot(MethodSpec.Builder methodSpec, CommandModel classModel, MethodModel method, CommandModel rootModel,
               String senderVarName) {
        this(methodSpec, classModel, method, rootModel, senderVarName, new LocalNames());
    }

    /**
     * Allocates the local for a parameter, named after the parameter the user declared.
     *
     * @param parameter the parameter to name
     * @param fallback  the name to use when the declared name is unusable
     * @return the local variable name
     */
    String localFor(ParameterModel parameter, String fallback) {
        return locals().allocate(LocalNames.declaredName(parameter), fallback);
    }
}
