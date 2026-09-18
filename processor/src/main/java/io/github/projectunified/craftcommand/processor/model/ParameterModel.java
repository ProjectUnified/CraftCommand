package io.github.projectunified.craftcommand.processor.model;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Model representing a command method parameter.
 *
 * <p>All annotation-derived facts are resolved once at parse time: the resolver declared by
 * {@code @Resolve} and the suggestion provider declared by {@code @Suggest} are linked here, so
 * code generation never re-reads annotations from the parameter element.
 */
public class ParameterModel {
    private final String name;
    private final TypeMirror type;
    private final boolean greedy;
    private final boolean optional;
    private final String defaultValue;
    private final String suggestProvider;
    private final String resolveName;
    private final MethodModel resolverMethod;
    private final MethodModel suggestMethod;
    private final VariableElement element;

    /**
     * Constructs a ParameterModel.
     *
     * @param name            the parameter name (from {@code @Name} or reflection)
     * @param type            the Java type of the parameter
     * @param greedy          {@code true} if marked with {@code @Greedy}
     * @param optional        {@code true} if marked with {@code @Default}
     * @param defaultValue    the default value string when optional, or {@code null}
     * @param suggestProvider the custom suggest provider name, or {@code null}
     * @param resolveName     the raw {@code @Resolve} value, {@code null} when {@code @Resolve} is absent and
     *                        empty when it is present without a resolver name
     * @param resolverMethod  the resolver method model referenced by a named {@code @Resolve}, or {@code null}
     * @param suggestMethod   the suggest provider method model when {@code @Suggest} names a valid method, or
     *                        {@code null} when absent, a field, or an invalid signature
     * @param element         the underlying VariableElement
     */
    public ParameterModel(String name, TypeMirror type, boolean greedy, boolean optional, String defaultValue, String suggestProvider, String resolveName, MethodModel resolverMethod, MethodModel suggestMethod, VariableElement element) {
        this.name = name;
        this.type = type;
        this.greedy = greedy;
        this.optional = optional;
        this.defaultValue = defaultValue;
        this.suggestProvider = suggestProvider;
        this.resolveName = resolveName;
        this.resolverMethod = resolverMethod;
        this.suggestMethod = suggestMethod;
        this.element = element;
    }

    /**
     * Gets the parameter name.
     *
     * @return the parameter name
     */
    public String getName() {
        return name;
    }

    /**
     * Gets the Java TypeMirror of this parameter.
     *
     * @return the parameter type
     */
    public TypeMirror getType() {
        return type;
    }

    /**
     * Checks if this parameter is greedy.
     *
     * @return {@code true} if greedy
     */
    public boolean isGreedy() {
        return greedy;
    }

    /**
     * Checks if this parameter is optional.
     *
     * @return {@code true} if optional
     */
    public boolean isOptional() {
        return optional;
    }

    /**
     * Gets the default value string.
     *
     * @return the default value, or {@code null}
     */
    public String getDefaultValue() {
        return defaultValue;
    }

    /**
     * Gets the custom suggestion provider name.
     *
     * @return the suggestion provider name, or {@code null}
     */
    public String getSuggestProvider() {
        return suggestProvider;
    }

    /**
     * Gets the raw {@code @Resolve} value of this parameter.
     *
     * @return the resolver name, {@code null} when {@code @Resolve} is absent, empty when present without a name
     */
    public String getResolveName() {
        return resolveName;
    }

    /**
     * Gets the resolver method linked by a named {@code @Resolve}.
     *
     * @return the resolver method model, or {@code null} when there is no named resolver
     */
    public MethodModel getResolverMethod() {
        return resolverMethod;
    }

    /**
     * Gets the suggest provider method linked by {@code @Suggest}.
     *
     * @return the suggest method model, or {@code null} when there is none
     */
    public MethodModel getSuggestMethod() {
        return suggestMethod;
    }

    /**
     * Gets the underlying VariableElement.
     *
     * @return the variable element
     */
    public VariableElement getElement() {
        return element;
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
        return new ParameterModel(name, type, greedy, optional, defaultValue, suggestProvider, resolveName, resolverMethod, suggestMethod, element);
    }

    /**
     * Creates an optional copy of this parameter using the default value of a parent parameter.
     *
     * @param inheritedDefault the default value inherited from the parent parameter
     * @return a parameter model marked optional with the inherited default
     */
    public ParameterModel asOptional(String inheritedDefault) {
        return new ParameterModel(name, type, greedy, true, inheritedDefault, suggestProvider, resolveName, resolverMethod, suggestMethod, element);
    }
}
