package io.github.projectunified.craftcommand;

import java.util.List;

/**
 * Information about a command or subcommand execution.
 */
public class CommandInfo {
    private final List<String> path;
    private final String usage;
    private final String description;

    /**
     * Constructs a CommandInfo.
     *
     * @param path        the subcommand path
     * @param usage       the usage syntax
     * @param description the description
     */
    public CommandInfo(List<String> path, String usage, String description) {
        this.path = path;
        this.usage = usage;
        this.description = description;
    }

    /**
     * Gets the subcommand path.
     *
     * @return the path
     */
    public List<String> getPath() {
        return path;
    }

    /**
     * Gets the usage syntax.
     *
     * @return the usage syntax
     */
    public String getUsage() {
        return usage;
    }

    /**
     * Gets the description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }
}
