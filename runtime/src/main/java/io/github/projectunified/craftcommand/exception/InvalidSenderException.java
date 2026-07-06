package io.github.projectunified.craftcommand.exception;

/**
 * Exception thrown when the command executor is not of the required sender type.
 */
public class InvalidSenderException extends CommandException {
    private final Class<?> requiredType;

    /**
     * Constructs an InvalidSenderException with the specified required type and message.
     *
     * @param requiredType the required sender class type
     * @param message      the error message
     */
    public InvalidSenderException(Class<?> requiredType, String message) {
        super(message);
        this.requiredType = requiredType;
    }

    /**
     * Constructs an InvalidSenderException with a default message for the required type.
     *
     * @param requiredType the required sender class type
     */
    public InvalidSenderException(Class<?> requiredType) {
        super("Only " + requiredType.getSimpleName() + " can execute this command.");
        this.requiredType = requiredType;
    }

    /**
     * Gets the required sender class type.
     *
     * @return the required class type
     */
    public Class<?> getRequiredType() {
        return requiredType;
    }
}
