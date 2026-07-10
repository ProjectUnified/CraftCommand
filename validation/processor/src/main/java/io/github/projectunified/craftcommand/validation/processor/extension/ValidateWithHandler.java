package io.github.projectunified.craftcommand.validation.processor.extension;

import com.palantir.javapoet.MethodSpec;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.ResolverLookup;
import io.github.projectunified.craftcommand.processor.extension.ParameterAnnotationHandler;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;
import io.github.projectunified.craftcommand.validation.annotation.ValidateWith;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

/**
 * Built-in validation handler for {@code @ValidateWith} annotations.
 */
public class ValidateWithHandler implements ParameterAnnotationHandler<ValidateWith> {
    @Override
    public Class<ValidateWith> annotationType() {
        return ValidateWith.class;
    }

    @Override
    public void handle(ValidateWith annotation, ParameterModel parameter, String varName, String instanceExpr, String senderVar, MethodSpec.Builder methodSpec) {
        String methodName = annotation.value();

        // Find the method in the enclosing class to see the parameter count
        TypeElement typeElement = (TypeElement) parameter.getElement().getEnclosingElement().getEnclosingElement();
        ExecutableElement validateMethod = ResolverLookup.findMethod(typeElement, methodName);

        if (validateMethod == null) {
            throw new IllegalArgumentException("Could not find validation method '" + methodName + "' in class " + typeElement.getSimpleName());
        }

        methodSpec.addComment("Validate parameter '" + parameter.getName() + "' using custom validation method: " + methodName);
        methodSpec.beginControlFlow("try");

        int paramCount = validateMethod.getParameters().size();
        if (paramCount == 0) {
            methodSpec.addStatement("$L.$L()", instanceExpr, methodName);
        } else if (paramCount == 1) {
            methodSpec.addStatement("$L.$L($L)", instanceExpr, methodName, varName);
        } else if (paramCount == 2) {
            methodSpec.addStatement("$L.$L($L, $L)", instanceExpr, methodName, senderVar, varName);
        } else {
            throw new IllegalArgumentException("Validation method '" + methodName + "' in class " + typeElement.getSimpleName() + " must accept 0, 1, or 2 parameters.");
        }

        methodSpec.nextControlFlow("catch ($T e)", Exception.class);
        String messageKey = annotation.message().isEmpty() ? "validation.custom" : annotation.message();
        String defaultTemplate = annotation.message().isEmpty() ? "%2$s" : annotation.message();
        methodSpec.addStatement("throw new $T(manager.formatMessage($S, $S, $S, e.getMessage()), e)",
                CommandException.class, messageKey, defaultTemplate, parameter.getName());
        methodSpec.endControlFlow();
    }
}
