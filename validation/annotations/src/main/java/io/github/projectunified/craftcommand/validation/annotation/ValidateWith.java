package io.github.projectunified.craftcommand.validation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates a parameter by invoking a custom validation method inside the command class.
 * If the validation method throws an exception, it is caught and wrapped in a {@code ValidationException}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface ValidateWith {
    /**
     * The name of the validation method inside the command class.
     *
     * @return the validation method name
     */
    String value();

    /**
     * Custom message or translation key when validation fails.
     * Supports placeholders like {@code %1$s} for parameter name and {@code %2$s} for the thrown exception message.
     *
     * @return the error message or translation key
     */
    String message() default "";
}
