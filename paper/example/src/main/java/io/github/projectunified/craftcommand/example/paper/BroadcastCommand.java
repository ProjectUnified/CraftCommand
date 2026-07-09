package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.*;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.List;

@Command(value = "broadcast", aliases = {"bc"}, description = "Broadcasts a message to all players")
@Permission("example.broadcast")
public class BroadcastCommand {

    public final List<String> demoSuggests = Arrays.asList("test", "string");

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

    public CustomSender resolveCustomSender(CommandSourceStack stack) {
        return new CustomSender(stack.getSender());
    }

    public CustomString resolveCustomString(CustomSender stack, @Suggest("demoSuggests") String current) {
        return new CustomString(current);
    }

    @Command("custom")
    public void executeCustom(@Resolve("resolveCustomSender") CustomSender sender, @Resolve("resolveCustomString") CustomString string) {

    }

    public enum BroadcastType {
        MESSAGE,
        ACTION_BAR
    }

    public class CustomSender {
        private final CommandSender commandSender;

        public CustomSender(CommandSender commandSender) {
            this.commandSender = commandSender;
        }
    }

    public class CustomString {
        private final String s;

        public CustomString(String s) {
            this.s = s;
        }
    }
}
