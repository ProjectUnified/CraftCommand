package io.github.projectunified.craftcommand.paper;

import io.papermc.paper.command.brigadier.BasicCommand;

import java.util.Collection;

/**
 * Interface for Paper BasicCommand wrappers.
 */
public interface PaperBasicCommand extends BasicCommand {
    /**
     * Gets the main name of the command.
     *
     * @return the command name
     */
    String getName();

    /**
     * Gets the description of the command.
     *
     * @return the description
     */
    String getDescription();

    /**
     * Gets all registered aliases for the command.
     *
     * @return the command aliases
     */
    Collection<String> getAliases();
}
