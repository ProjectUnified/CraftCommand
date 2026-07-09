package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import org.bukkit.command.CommandSender;

/**
 * Covers @Permission: class-level, method-level, custom message, method override.
 */
@Command(value = "permtest", description = "Permission test commands")
@Permission("example.perm")
public class PermissionCommand {

    @Command("allowed")
    public void allowed(CommandSender sender) {
        sender.sendMessage("allowed");
    }

    @Command("methodperm")
    @Permission("example.perm.admin")
    public void methodPerm(CommandSender sender) {
        sender.sendMessage("methodperm");
    }

    @Command("custommsg")
    @Permission(value = "example.perm.secret", message = "Access denied to secret area!")
    public void customMsg(CommandSender sender) {
        sender.sendMessage("custommsg");
    }

    @Command("override")
    @Permission("example.perm.override")
    public void overridePerm(CommandSender sender) {
        sender.sendMessage("override");
    }
}
