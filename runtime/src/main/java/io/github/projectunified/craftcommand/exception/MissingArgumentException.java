package io.github.projectunified.craftcommand.exception;

/**
 * Exception thrown when a required command argument is missing.
 */
public class MissingArgumentException extends CommandException {
    private final String parameterName;

    /**
     * Constructs a MissingArgumentException with the specified parameter name and message.
     *
     * @param parameterName the name of the missing parameter
     * @param message       the error message
     */
    public MissingArgumentException(String parameterName, String message) {
        super(message);
        this.parameterName = parameterName;
    }

    /**
     * Constructs a MissingArgumentException with a default message.
     *
     * @param parameterName the name of the missing parameter
     */
    public MissingArgumentException(String parameterName) {
        super("Missing arguments for parameter: " + parameterName);
        this.parameterName = parameterName;
    }

    /**
     * Gets the name of the missing parameter.
     *
     * @return the parameter name
     */
    public String getParameterName() {
        return parameterName;
    }
}
