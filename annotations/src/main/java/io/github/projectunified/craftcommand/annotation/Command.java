package io.github.projectunified.craftcommand.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Defines a command or subcommand.
 *
 * <p>On a class: marks it as the main command entry point.
 * On a method or nested class: marks it as a subcommand.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Command {
    /**
     * Command or subcommand name.
     */
    String value();

    /**
     * Alternative names for this command.
     */
    String[] aliases() default {};

    /**
     * Command description. Prefix with {@code i18n:} for runtime i18n lookup.
     * e.g. {@code "Static text"} or {@code "i18n:commands.cmd.desc"}.
     */
    String description() default "";
}
