package io.github.projectunified.craftcommand.standalone;

import io.github.projectunified.craftcommand.CommandInfo;

import java.util.List;

/**
 * Interface representing a generated standalone command executor.
 */
public interface StandaloneCommand {
    /**
     * Gets the primary name of the command.
     *
     * @return the command name
     */
    String getName();

    /**
     * Gets the aliases of the command.
     *
     * @return the list of command aliases
     */
    List<String> getAliases();

    /**
     * Gets the description of the command.
     *
     * @return the command description
     */
    String getDescription();

    /**
     * Executes the command.
     *
     * @param sender the sender executing the command
     * @param args   the arguments passed to the command
     * @return {@code true} if execution succeeded
     */
    boolean execute(Object sender, String[] args);

    /**
     * Tab-completes the command.
     *
     * @param sender the sender completing the command
     * @param args   the arguments entered so far
     * @return a list of suggestions
     */
    List<String> tabComplete(Object sender, String[] args);

    /**
     * Gets the command metadata.
     *
     * @return the command info list
     */
    List<CommandInfo> getCommandInfo();
}
