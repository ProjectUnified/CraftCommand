package io.github.projectunified.craftcommand.exception;

/**
 * Exception thrown when parameter validation fails (e.g. from {@code @Min}, {@code @Max}, or {@code @ValidateWith}).
 */
public class ValidationException extends CommandException {
    private final String parameterName;

    /**
     * Constructs a ValidationException with the specified parameter name and message.
     *
     * @param parameterName the name of the validated parameter
     * @param message       the error message
     */
    public ValidationException(String parameterName, String message) {
        super(message);
        this.parameterName = parameterName;
    }

    /**
     * Constructs a ValidationException with the specified parameter name, message, and cause.
     *
     * @param parameterName the name of the validated parameter
     * @param message       the error message
     * @param cause         the underlying cause
     */
    public ValidationException(String parameterName, String message, Throwable cause) {
        super(message, cause);
        this.parameterName = parameterName;
    }

    /**
     * Gets the name of the validated parameter.
     *
     * @return the parameter name
     */
    public String getParameterName() {
        return parameterName;
    }
}
