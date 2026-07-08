package io.github.projectunified.craftcommand.processor.extension;

import com.palantir.javapoet.MethodSpec;
import io.github.projectunified.craftcommand.processor.model.MethodModel;

import java.lang.annotation.Annotation;

/**
 * SPI handler for method-level validation and execution wrapping annotations.
 *
 * <p>Unlike {@link MethodAnnotationHandler} which injects code before parameter resolution,
 * {@link CommandValidator} can wrap the entire execution block (for cooldowns, async, etc.).
 *
 * @param <A> the annotation type
 */
public interface CommandValidator<A extends Annotation> {

    /**
     * Returns the annotation type this validator targets.
     *
     * @return the annotation class
     */
    Class<A> annotationType();

    /**
     * Wraps the execution of the command method with validation or async logic.
     * Called after all parameters are resolved and before the method invocation.
     *
     * @param annotation   the annotation instance
     * @param method       the method model
     * @param instanceExpr the expression of the target command instance
     * @param senderVar    the variable name of the resolved sender
     * @param methodSpec   the method spec builder to add wrapping code to
     */
    void wrap(A annotation, MethodModel method, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec);
}
