package io.github.projectunified.craftcommand.paper;

import com.mojang.brigadier.tree.LiteralCommandNode;
import io.github.projectunified.craftcommand.CommandInfo;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Collection;
import java.util.List;

/**
 * Interface for Paper command wrappers.
 */
public interface PaperCommand {
    /**
     * Gets the Brigadier command node.
     *
     * @return the command node
     */
    LiteralCommandNode<CommandSourceStack> getCommandNode();

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

    /**
     * Gets the command metadata.
     *
     * @return the command info list
     */
    List<CommandInfo> getCommandInfo();
}
