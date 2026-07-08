package io.github.projectunified.craftcommand;

import java.util.List;

/**
 * Metadata for a command or subcommand (path, usage, description).
 */
public class CommandInfo {
    private final List<String> path;
    private final String usage;
    private final String description;

    public CommandInfo(List<String> path, String usage, String description) {
        this.path = path;
        this.usage = usage;
        this.description = description;
    }

    /**
     * Command path (e.g. ["teleport", "player"]).
     */
    public List<String> getPath() {
        return path;
    }

    /**
     * Usage syntax (e.g. {@code <target>}).
     */
    public String getUsage() {
        return usage;
    }

    /**
     * Description string.
     */
    public String getDescription() {
        return description;
    }
}
