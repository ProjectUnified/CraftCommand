package io.github.projectunified.craftcommand.validation.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Validates that a numeric parameter value is at least the specified minimum.
 * If validation fails, throws a {@code ValidationException}.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface Min {
    /**
     * The minimum allowed value (inclusive).
     *
     * @return the minimum value
     */
    double value();

    /**
     * Custom message or translation key when validation fails.
     * Supports placeholders like {@code %1$s} for parameter name and {@code %2$s} for minimum value.
     *
     * @return the error message or translation key
     */
    String message() default "";
}
