package io.github.projectunified.craftcommand.validation.processor.extension;

import com.palantir.javapoet.MethodSpec;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.generator.TypeSupport;
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
        String typeName = parameter.type().toString();
        if (!TypeSupport.isNumericType(typeName)) {
            throw new IllegalArgumentException("@Min can only be applied to numeric parameters, but '" + parameter.name() + "' is of type " + typeName);
        }
        String messageKey = annotation.message().isEmpty() ? "validation.min" : annotation.message();
        String defaultTemplate = annotation.message().isEmpty()
                ? "%s cannot be less than %s"
                : annotation.message();
        boolean isInt = TypeSupport.isIntegerType(typeName) && annotation.value() == Math.floor(annotation.value()) && !Double.isInfinite(annotation.value());
        Object limitValue;
        if (isInt) {
            limitValue = (long) annotation.value();
        } else {
            limitValue = annotation.value();
        }
        methodSpec.beginControlFlow("if ($L < $L)", varName, limitValue)
                .addStatement("throw new $T(manager.formatMessage($S, $S, $S, $L))",
                        CommandException.class, messageKey, defaultTemplate, parameter.name(), limitValue)
                .endControlFlow();
    }
}
