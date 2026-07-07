package io.github.projectunified.craftcommand.processor.model;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Model representing a command or subcommand method.
 */
public class MethodModel {
    private final String methodName;
    private final String subcommandName;
    private final List<String> aliases;
    private final String description;
    private final ParameterModel senderParameter;
    private final List<ParameterModel> parameters;
    private final boolean isDefault;
    private final ExecutableElement element;

    /**
     * Constructs a MethodModel.
     *
     * @param methodName      the name of the Java method
     * @param subcommandName  the name of the subcommand, or {@code null} if default action
     * @param aliases         the subcommand aliases
     * @param description       the subcommand description
     * @param senderParameter the model for the first (sender) parameter
     * @param parameters      the models for the remaining command arguments
     * @param isDefault       {@code true} if marked with {@code @Default}
     * @param element         the underlying ExecutableElement
     */
    public MethodModel(String methodName, String subcommandName, List<String> aliases, String description, ParameterModel senderParameter, List<ParameterModel> parameters, boolean isDefault, ExecutableElement element) {
        this.methodName = methodName;
        this.subcommandName = subcommandName;
        this.aliases = aliases;
        this.description = description;
        this.senderParameter = senderParameter;
        this.parameters = parameters;
        this.isDefault = isDefault;
        this.element = element;
    }

    /**
     * Gets the Java method name.
     *
     * @return the method name
     */
    public String getMethodName() {
        return methodName;
    }

    /**
     * Gets the subcommand name.
     *
     * @return the subcommand name, or {@code null}
     */
    public String getSubcommandName() {
        return subcommandName;
    }

    /**
     * Gets the subcommand aliases.
     *
     * @return the list of aliases
     */
    public List<String> getAliases() {
        return aliases;
    }

    /**
     * Gets the subcommand description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the type of the sender parameter.
     *
     * @return the sender type mirror
     */
    public TypeMirror getSenderType() {
        return senderParameter.getType();
    }

    /**
     * Gets the sender parameter model.
     *
     * @return the sender parameter model
     */
    public ParameterModel getSenderParameter() {
        return senderParameter;
    }

    /**
     * Gets the list of argument parameters.
     *
     * @return the list of parameter models
     */
    public List<ParameterModel> getParameters() {
        return parameters;
    }

    /**
     * Checks if this method is the default action.
     *
     * @return {@code true} if default action
     */
    public boolean isDefault() {
        return isDefault;
    }

    /**
     * Gets the underlying Java ExecutableElement.
     *
     * @return the executable element
     */
    public ExecutableElement getElement() {
        return element;
    }
}
