package io.github.projectunified.craftcommand.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Binds a parameter to a local resolver method, or marks a method as a resolver.
 *
 * <p>On a method: declares it as a resolver for its return type.
 * On a parameter: binds to a resolver by name (value).
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface Resolve {
    /**
     * Resolver method name. Optional on methods, required on parameters.
     */
    String value() default "";
}
