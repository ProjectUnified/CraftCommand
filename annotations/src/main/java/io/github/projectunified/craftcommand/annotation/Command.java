package io.github.projectunified.craftcommand.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to define a main command.
 * Apply this to a class to mark it as a command entry point.
 */
@Target(ElementType.TYPE)
@Retention(RetentionPolicy.CLASS)
public @interface Command {
    /**
     * The primary name of the command.
     *
     * @return the command name
     */
    String value();

    /**
     * The aliases of the command.
     *
     * @return the command aliases
     */
    String[] aliases() default {};

    /**
     * The description of the command.
     *
     * @return the command description
     */
    String description() default "";
}
