package io.github.projectunified.craftcommand.processor.model;

import javax.lang.model.element.ExecutableElement;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Model representing a command or subcommand method.
 *
 * @param methodName the Java method name
 * @param subcommandName the subcommand name, or {@code null}
 * @param aliases the subcommand aliases
 * @param description the subcommand description
 * @param senderParameter the sender parameter
 * @param parameters the command argument parameters
 * @param isDefault whether this is the default action
 * @param element the underlying method element
 */
public record MethodModel(String methodName, String subcommandName, List<String> aliases, String description,
                           ParameterModel senderParameter, List<ParameterModel> parameters, boolean isDefault,
                           ExecutableElement element) {
    public MethodModel {
        description = description != null ? description : "";
    }

    /**
     * Gets the type of the sender parameter.
     *
     * @return the sender type mirror
     */
    public TypeMirror getSenderType() {
        return senderParameter.type();
    }

}
