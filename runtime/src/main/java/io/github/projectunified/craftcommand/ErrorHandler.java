package io.github.projectunified.craftcommand;

/**
 * Handler for exceptions thrown during command execution.
 *
 * @param <S> the command sender type
 */
public interface ErrorHandler<S> {
    /**
     * Handles an exception thrown during command execution.
     *
     * @param sender    the sender who executed the command
     * @param exception the exception that was thrown
     */
    void handle(S sender, Exception exception);
}
