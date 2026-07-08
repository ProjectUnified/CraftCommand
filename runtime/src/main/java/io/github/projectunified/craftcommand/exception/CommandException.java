package io.github.projectunified.craftcommand.exception;

/**
 * Thrown by command methods to signal validation or execution errors.
 */
public class CommandException extends RuntimeException {
    public CommandException(String message) {
        super(message);
    }

    public CommandException(String message, Throwable cause) {
        super(message, cause);
    }
}
