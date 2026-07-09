package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

/**
 * Covers sender type variations: CommandSourceStack, CommandSender, mixed.
 * Mirror of Bukkit SenderTypeCommand.
 */
@Command(value = "psendtest", description = "Paper sender type test commands")
public class PaperSenderTypeCommand {

    @Command("css")
    public void cssSender(CommandSourceStack sender) {
        sender.getSender().sendMessage("css=" + sender.getSender().getName());
    }

    @Command("cs")
    public void csSender(CommandSender sender) {
        sender.sendMessage("cs=" + sender.getName());
    }

    @Command("default")
    public void defaultSender(CommandSourceStack sender) {
        sender.getSender().sendMessage("default=" + sender.getSender().getName());
    }
}
