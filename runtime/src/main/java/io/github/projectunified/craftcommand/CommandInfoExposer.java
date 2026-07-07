package io.github.projectunified.craftcommand;

import java.util.List;

/**
 * Interface implemented by generated command wrappers to expose their command info list.
 */
public interface CommandInfoExposer {
    /**
     * Gets the list of command information.
     *
     * @return the list of command information
     */
    List<CommandInfo> getCommandInfo();
}
