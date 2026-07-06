package io.github.projectunified.craftcommand.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation marking a command parameter as optional.
 * When omitted by the caller, the parameter resolves to the specified default value.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface Optional {
    /**
     * The default value (as a String) when the argument is omitted.
     *
     * @return the default value
     */
    String value() default "";
}
