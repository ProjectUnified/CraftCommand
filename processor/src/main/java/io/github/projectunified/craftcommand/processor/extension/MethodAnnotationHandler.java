package io.github.projectunified.craftcommand.processor.extension;

import com.palantir.javapoet.MethodSpec;
import io.github.projectunified.craftcommand.processor.model.MethodModel;

import java.lang.annotation.Annotation;

/**
 * SPI handler to inject custom validation or execution logic based on method-level annotations.
 *
 * @param <A> the type of annotation
 */
public interface MethodAnnotationHandler<A extends Annotation> {
    /**
     * Gets the annotation type this handler targets.
     *
     * @return the annotation class
     */
    Class<A> annotationType();

    /**
     * Injects custom code (e.g. permission/cooldown checks) into the generated method execution code block.
     * Called before parameter resolution and method invocation.
     *
     * @param annotation   the method annotation instance
     * @param method       the method model
     * @param instanceExpr the expression of the target command instance (e.g. "instance" or "this.subInstance_xxx")
     * @param senderVar    the variable name representing the command sender (e.g. "senderCast")
     * @param methodSpec   the method spec builder for the execute/onCommand method
     */
    void handle(A annotation, MethodModel method, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec);
}
