package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.annotation.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

/**
 * Covers sender type variations: Player, CommandSender, mixed.
 */
@Command(value = "sendtest", description = "Sender type test commands")
public class SenderTypeCommand {

    @Command("player")
    public void playerSender(Player sender) {
        sender.sendMessage("player=" + sender.getName());
    }

    @Command("commandsender")
    public void commandSenderSender(CommandSender sender) {
        sender.sendMessage("commandsender=" + sender.getName());
    }

    @Command("default")
    public void defaultSender(CommandSender sender) {
        sender.sendMessage("default=" + sender.getName());
    }
}
