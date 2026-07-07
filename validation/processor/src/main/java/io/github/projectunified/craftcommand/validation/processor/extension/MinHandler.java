package io.github.projectunified.craftcommand.validation.processor.extension;

import com.palantir.javapoet.MethodSpec;
import io.github.projectunified.craftcommand.exception.ValidationException;
import io.github.projectunified.craftcommand.processor.extension.ParameterAnnotationHandler;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;
import io.github.projectunified.craftcommand.validation.annotation.Min;

/**
 * Built-in validation handler for {@code @Min} annotations.
 */
public class MinHandler implements ParameterAnnotationHandler<Min> {
    @Override
    public Class<Min> annotationType() {
        return Min.class;
    }

    @Override
    public void handle(Min annotation, ParameterModel parameter, String varName, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec) {
        String messageKey = annotation.message().isEmpty() ? "validation.min" : annotation.message();
        String defaultTemplate = annotation.message().isEmpty()
                ? "%s cannot be less than %s"
                : annotation.message();
        methodSpec.addComment("Validate parameter '" + parameter.getName() + "' against min limit: " + annotation.value());
        methodSpec.beginControlFlow("if ($L < $L)", varName, annotation.value())
                .addStatement("throw new $T($S, manager.formatMessage($S, $S, $S, $L))",
                        ValidationException.class, parameter.getName(), messageKey, defaultTemplate, parameter.getName(), annotation.value())
                .endControlFlow();
    }
}
