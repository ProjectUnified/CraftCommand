package io.github.projectunified.craftcommand.processor.model;

import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;

/**
 * Model representing a command method parameter.
 */
public class ParameterModel {
    private final String name;
    private final TypeMirror type;
    private final boolean greedy;
    private final boolean optional;
    private final String defaultValue;
    private final String suggestProvider;
    private final VariableElement element;

    /**
     * Constructs a ParameterModel.
     *
     * @param name            the parameter name (from {@code @Name} or reflection)
     * @param type            the Java type of the parameter
     * @param greedy          {@code true} if marked with {@code @Greedy}
     * @param optional        {@code true} if marked with {@code @Optional}
     * @param defaultValue    the default value string when optional, or {@code null}
     * @param suggestProvider the custom suggest provider method name, or {@code null}
     * @param element         the underlying VariableElement
     */
    public ParameterModel(String name, TypeMirror type, boolean greedy, boolean optional, String defaultValue, String suggestProvider, VariableElement element) {
        this.name = name;
        this.type = type;
        this.greedy = greedy;
        this.optional = optional;
        this.defaultValue = defaultValue;
        this.suggestProvider = suggestProvider;
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
     * Gets the custom suggestion provider method name.
     *
     * @return the suggestion provider name, or {@code null}
     */
    public String getSuggestProvider() {
        return suggestProvider;
    }

    /**
     * Gets the underlying VariableElement.
     *
     * @return the variable element
     */
    public VariableElement getElement() {
        return element;
    }
}
