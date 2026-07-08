package io.github.projectunified.craftcommand.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Dual-purpose annotation.
 *
 * <p><b>On methods:</b> Marks the default action when no subcommand matches.
 * {@link #value()} must be empty.
 *
 * <p><b>On parameters:</b> Marks as optional. {@link #value()} is the default value string.
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface Default {
    /**
     * Default value string for optional parameters. Empty on methods.
     */
    String value() default "";
}
