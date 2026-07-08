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
     * The custom error message when permission is denied.
     * Prefix with {@code i18n:} for runtime i18n lookup.
     * e.g. {@code "No permission!"} or {@code "i18n:fail.no-permission"}.
     *
     * @return the error message
     */
    String message() default "";
}
