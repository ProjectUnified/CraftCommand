package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

/**
 * Covers Paper-specific sender resolution: CommandSourceStack, Player via cast helper.
 */
@Command(value = "papersend", description = "Paper sender test commands")
public class PaperSenderCommand {

    @Command("css")
    public void cssSender(CommandSourceStack sender) {
        sender.getSender().sendMessage("css=" + sender.getSender().getName());
    }

    @Command("cs")
    public void csSender(CommandSender sender) {
        sender.sendMessage("cs=" + sender.getName());
    }
}
