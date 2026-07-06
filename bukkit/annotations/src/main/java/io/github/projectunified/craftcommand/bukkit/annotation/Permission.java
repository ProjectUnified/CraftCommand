package io.github.projectunified.craftcommand.bukkit.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Annotation specifying the required Bukkit permission to execute a command or subcommand.
 */
@Target({ElementType.TYPE, ElementType.METHOD})
@Retention(RetentionPolicy.CLASS)
public @interface Permission {
    /**
     * The permission string (e.g. {@code "myplugin.admin"}).
     *
     * @return the permission required
     */
    String value();

    /**
     * The custom error message to send when the player does not have permission.
     * If empty, a default message will be used.
     *
     * @return the error message
     */
    String message() default "";
}
