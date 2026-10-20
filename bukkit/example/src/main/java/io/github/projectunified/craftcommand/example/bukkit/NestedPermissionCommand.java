package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;

/**
 * Covers a nested command class with its own permission and no methods.
 */
@Command("nestedperm")
public class NestedPermissionCommand {
    @Command("empty")
    @Permission("nestedperm.empty")
    public static class Empty {
    }
}
