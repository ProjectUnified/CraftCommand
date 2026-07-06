package io.github.projectunified.craftcommand.annotation;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Declares a local resolver method inside a command class, or binds a parameter to one.
 * When placed on a method, it marks it as a resolver.
 * When placed on a parameter, it binds the parameter to a specific resolver by name.
 */
@Target({ElementType.METHOD, ElementType.PARAMETER})
@Retention(RetentionPolicy.CLASS)
public @interface Resolve {
    /**
     * The name of the resolver.
     * If placed on a method, this specifies an optional name to reference this resolver.
     * If placed on a parameter, this specifies the name of the resolver method to bind to.
     *
     * @return the resolver name
     */
    String value() default "";
}
