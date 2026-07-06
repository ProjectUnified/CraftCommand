package io.github.projectunified.craftcommand.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to define a custom name for a command parameter.
 * This name will be displayed in command usages and exception messages.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface Name {
    /**
     * The custom name of the parameter.
     *
     * @return the parameter name
     */
    String value();
}
