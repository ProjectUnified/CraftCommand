package io.github.projectunified.craftcommand;

import java.util.List;

/**
 * Common interface for all generated command wrappers.
 */
public interface BaseCommand {
    /**
     * Gets the command metadata.
     *
     * @return the command info list
     */
    List<CommandInfo> getCommandInfo();
}
