package io.github.projectunified.craftcommand.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a command parameter to a suggestion provider method inside the command class.
 * This method is invoked when tab completing arguments for the parameter.
 */
@Target(ElementType.PARAMETER)
@Retention(RetentionPolicy.CLASS)
public @interface Suggest {
    /**
     * The name of the suggestion provider method inside the command class.
     *
     * @return the suggestion provider method name
     */
    String value();
}
