package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/**
 * Covers @Permission: class-level, method-level, custom message, method override.
 * Mirror of Bukkit PermissionCommand with CommandSourceStack sender.
 */
@Command(value = "ppermtest", description = "Paper permission test commands")
@Permission("example.perm")
public class PaperPermissionCommand {

    @Command("allowed")
    public void allowed(CommandSourceStack sender) {
        sender.getSender().sendMessage("allowed");
    }

    @Command("methodperm")
    @Permission("example.perm.admin")
    public void methodPerm(CommandSourceStack sender) {
        sender.getSender().sendMessage("methodperm");
    }

    @Command("custommsg")
    @Permission(value = "example.perm.secret", message = "Access denied to secret area!")
    public void customMsg(CommandSourceStack sender) {
        sender.getSender().sendMessage("custommsg");
    }

    @Command("override")
    @Permission("example.perm.override")
    public void overridePerm(CommandSourceStack sender) {
        sender.getSender().sendMessage("override");
    }
}
