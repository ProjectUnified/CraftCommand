package io.github.projectunified.craftcommand.processor.extension;

import com.palantir.javapoet.MethodSpec;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import java.lang.annotation.Annotation;

/**
 * SPI handler to inject custom validation or execution logic based on parameter-level annotations.
 *
 * @param <A> the type of annotation
 */
public interface ParameterAnnotationHandler<A extends Annotation> {
    /**
     * Gets the annotation type this handler targets.
     *
     * @return the annotation class
     */
    Class<A> annotationType();

    /**
     * Injects custom code (e.g. validation) into the generated method execution code block.
     * Called after the parameter value is resolved and assigned to the parameter variable.
     *
     * @param annotation   the parameter annotation instance
     * @param parameter    the parameter model
     * @param varName      the generated local holding the parameter value (for example {@code "amount"}, or
     *                     {@code "param_1"} when the declared name would shadow a generated local)
     * @param instanceExpr the expression of the target command instance (e.g. "instance" or "this.subInstance_xxx")
     * @param senderVar    the variable name representing the command sender (e.g. "senderCast")
     * @param methodSpec   the method spec builder for the execute/onCommand method
     */
    void handle(A annotation, ParameterModel parameter, String varName, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec);
}
