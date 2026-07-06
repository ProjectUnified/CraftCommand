package io.github.projectunified.craftcommand.validation.processor.extension;

import com.palantir.javapoet.MethodSpec;
import io.github.projectunified.craftcommand.exception.ValidationException;
import io.github.projectunified.craftcommand.processor.extension.ParameterAnnotationHandler;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;
import io.github.projectunified.craftcommand.validation.annotation.Max;

/**
 * Built-in validation handler for {@code @Max} annotations.
 */
public class MaxHandler implements ParameterAnnotationHandler<Max> {
    @Override
    public Class<Max> annotationType() {
        return Max.class;
    }

    @Override
    public void handle(Max annotation, ParameterModel parameter, String varName, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec) {
        String messageKey = annotation.message().isEmpty() ? "validation.max" : annotation.message();
        String defaultTemplate = annotation.message().isEmpty()
                ? "%s cannot be greater than %s"
                : annotation.message();
        methodSpec.beginControlFlow("if ($L > $L)", varName, annotation.value())
                .addStatement("throw new $T($S, manager.formatMessage($S, $S, $S, $L))",
                        ValidationException.class, parameter.getName(), messageKey, defaultTemplate, parameter.getName(), annotation.value())
                .endControlFlow();
    }
}
