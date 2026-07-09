package io.github.projectunified.craftcommand.validation.processor.extension;

import com.palantir.javapoet.MethodSpec;
import io.github.projectunified.craftcommand.exception.CommandException;
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
        String typeName = parameter.getType().toString();
        if (!isNumericType(typeName)) {
            throw new IllegalArgumentException("@Max can only be applied to numeric parameters, but '" + parameter.getName() + "' is of type " + typeName);
        }
        String messageKey = annotation.message().isEmpty() ? "validation.max" : annotation.message();
        String defaultTemplate = annotation.message().isEmpty()
                ? "%s cannot be greater than %s"
                : annotation.message();
        methodSpec.addComment("Validate parameter '" + parameter.getName() + "' against max limit: " + annotation.value());
        methodSpec.beginControlFlow("if ($L > $L)", varName, annotation.value())
                .addStatement("throw new $T(manager.formatMessage($S, $S, $S, $L))",
                        CommandException.class, messageKey, defaultTemplate, parameter.getName(), annotation.value())
                .endControlFlow();
    }

    private boolean isNumericType(String typeName) {
        switch (typeName) {
            case "int":
            case "long":
            case "double":
            case "float":
            case "short":
            case "byte":
            case "java.lang.Integer":
            case "java.lang.Long":
            case "java.lang.Double":
            case "java.lang.Float":
            case "java.lang.Short":
            case "java.lang.Byte":
            case "java.lang.Number":
                return true;
            default:
                return false;
        }
    }
}
