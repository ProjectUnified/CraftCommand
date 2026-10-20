package io.github.projectunified.craftcommand.processor.model;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Model representing a command method parameter.
 *
 * <p>All annotation-derived facts are resolved once at parse time: the resolver declared by
 * {@code @Resolve} and the suggestion provider declared by {@code @Suggest} are linked here, so
 * code generation never re-reads the core command annotations. SPI handlers still read their own annotations.
 *
 * @param name the parameter name
 * @param type the Java type of the parameter
 * @param enumConstants the constants when the type is an enum, otherwise {@code null}
 * @param greedy whether the parameter consumes all remaining arguments
 * @param optional whether the parameter has a default value
 * @param defaultValue the default value, or {@code null}
 * @param suggestProvider the suggestion provider name, or {@code null}
 * @param suggestField whether the suggestion provider names a field
 * @param resolveName the raw {@code @Resolve} value, or {@code null}
 * @param resolverMethod the linked resolver method, or {@code null}
 * @param suggestMethod the linked suggestion method, or {@code null}
 * @param element the underlying parameter element
 */
public record ParameterModel(String name, TypeMirror type, List<String> enumConstants, boolean greedy,
                             boolean optional, String defaultValue, String suggestProvider, boolean suggestField,
                             String resolveName, MethodModel resolverMethod, MethodModel suggestMethod,
                             VariableElement element) {
    /**
     * Creates the sender parameter, which has no command argument, default, or suggestion state.
     *
     * @param name the sender parameter name
     * @param type the sender type
     * @param resolveName the raw {@code @Resolve} value
     * @param resolverMethod the linked resolver method, or {@code null}
     * @param element the underlying sender element
     */
    public static ParameterModel sender(String name, TypeMirror type, String resolveName,
                                        MethodModel resolverMethod, VariableElement element) {
        return new ParameterModel(name, type, null, false, false, null, null, false,
                resolveName, resolverMethod, null, element);
    }

    /**
     * Creates a copy of this parameter using the Java identifier as its name.
     *
     * <p>Used when handing the parameter to SPI handlers, which report source-level parameter names.
     *
     * @param name the replacement name
     * @return a parameter model with the given name
     */
    public ParameterModel withName(String name) {
        return new ParameterModel(name, type, enumConstants, greedy, optional, defaultValue, suggestProvider, suggestField, resolveName, resolverMethod, suggestMethod, element);
    }

    /**
     * Creates an optional copy of this parameter using the default value of a parent parameter.
     *
     * @param inheritedDefault the default value inherited from the parent parameter
     * @return a parameter model marked optional with the inherited default
     */
    public ParameterModel asOptional(String inheritedDefault) {
        return new ParameterModel(name, type, enumConstants, greedy, true, inheritedDefault, suggestProvider, suggestField, resolveName, resolverMethod, suggestMethod, element);
    }
}
