package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Default;
import io.github.projectunified.craftcommand.annotation.Greedy;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;

@Command(value = "broadcast", aliases = {"bc"}, description = "Broadcasts a message to all players")
@Permission("example.broadcast")
public class BroadcastCommand {

    @Default
    public void execute(CommandSender sender, @Greedy String message) {
        Bukkit.broadcastMessage("[Broadcast] " + message);
    }

    @Command("stack")
    public void executeStack(CommandSourceStack sender, @Greedy String message) {
        Bukkit.broadcastMessage("[Stack Broadcast] " + message);
    }

    @Command("type")
    public void executeType(CommandSender sender, BroadcastType type, @Greedy String message) {

    }

    public enum BroadcastType {
        MESSAGE,
        ACTION_BAR
    }
}
