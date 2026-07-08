package io.github.projectunified.craftcommand;

/**
 * Handles exceptions thrown during command execution.
 */
public interface ErrorHandler<S> {
    /**
     * Called when a command throws an exception.
     */
    void handle(S sender, Exception exception);
}
