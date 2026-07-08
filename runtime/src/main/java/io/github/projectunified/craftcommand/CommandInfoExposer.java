package io.github.projectunified.craftcommand;

import java.util.List;

/**
 * Implemented by generated wrappers to expose command metadata.
 */
public interface CommandInfoExposer {
    /**
     * Returns the command/subcommand tree info.
     */
    List<CommandInfo> getCommandInfo();
}
