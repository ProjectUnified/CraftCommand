package io.github.projectunified.craftcommand.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation used to define a subcommand.
 * Apply this to a method or a nested class to mark it as a subcommand of the enclosing command.
 */
@Target({ElementType.METHOD, ElementType.TYPE})
@Retention(RetentionPolicy.CLASS)
public @interface Subcommand {
    /**
     * The primary name of the subcommand.
     *
     * @return the subcommand name
     */
    String value();

    /**
     * The aliases of the subcommand.
     *
     * @return the subcommand aliases
     */
    String[] aliases() default {};

    /**
     * The description of the subcommand.
     *
     * @return the command description
     */
    String description() default "";
}
