package io.github.projectunified.craftcommand.validation.processor.extension;

import com.palantir.javapoet.MethodSpec;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.extension.ParameterAnnotationHandler;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;
import io.github.projectunified.craftcommand.validation.annotation.Min;

import javax.lang.model.element.Element;

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
        String typeName = parameter.getType().toString();
        if (!isNumericType(typeName)) {
            Element element = parameter.getElement();
            element.getEnclosingElement().getEnclosingElement().getEnclosingElement();
            // Use the element's processing environment to report the error
            // Since we don't have direct access to the processing environment here,
            // we throw an IllegalArgumentException which will be caught by the processor
            throw new IllegalArgumentException("@Min can only be applied to numeric parameters, but '" + parameter.getName() + "' is of type " + typeName);
        }
        String messageKey = annotation.message().isEmpty() ? "validation.min" : annotation.message();
        String defaultTemplate = annotation.message().isEmpty()
                ? "%s cannot be less than %s"
                : annotation.message();
        methodSpec.addComment("Validate parameter '" + parameter.getName() + "' against min limit: " + annotation.value());
        methodSpec.beginControlFlow("if ($L < $L)", varName, annotation.value())
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
