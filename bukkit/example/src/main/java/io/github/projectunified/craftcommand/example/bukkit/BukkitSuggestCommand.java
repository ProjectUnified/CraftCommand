package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Greedy;
import io.github.projectunified.craftcommand.annotation.Resolve;
import io.github.projectunified.craftcommand.annotation.Suggest;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Covers @Suggest in Bukkit context: field, method, @Greedy+@Suggest,
 * and suggest methods with different sender types.
 */
@Command(value = "buksug", description = "Bukkit suggest test commands")
public class BukkitSuggestCommand {

    public final List<String> colors = Arrays.asList("red", "green", "blue");

    // Signature 1: m(String[] current)
    public Collection<String> getShapes(String[] current) {
        return Arrays.asList("circle", "square", "triangle");
    }

    // Signature 3: m(CommandSender sender)
    public Collection<String> getShapesWithSender(CommandSender sender) {
        return Arrays.asList("circle", "square", "triangle");
    }

    // Signature 4: m(Player sender, String[] current)
    public Collection<String> getShapesWithPlayer(Player sender, String[] current) {
        return Arrays.asList("circle", "square", "triangle");
    }

    // Custom sender resolver
    public CustomSender resolveSender(CommandSender sender) {
        return new CustomSender(sender.getName(), sender);
    }

    // Signature 4: m(CustomSender sender, String[] current)
    public Collection<String> getShapesWithCustom(CommandSender sender, String[] current) {
        return Arrays.asList("circle", "square", "triangle");
    }

    @Command("field")
    public void suggestField(CommandSender sender, @Suggest("colors") String color) {
        sender.sendMessage("field=" + color);
    }

    @Command("method")
    public void suggestMethod(CommandSender sender, @Suggest("getShapes") String shape) {
        sender.sendMessage("method=" + shape);
    }

    @Command("methodsender")
    public void suggestMethodSender(CommandSender sender, @Suggest("getShapesWithSender") String shape) {
        sender.sendMessage("methodsender=" + shape);
    }

    @Command("methodplayer")
    public void suggestMethodPlayer(CommandSender sender, @Suggest("getShapesWithPlayer") String shape) {
        sender.sendMessage("methodplayer=" + shape);
    }

    @Command("methodcustom")
    public void suggestMethodCustom(@Resolve("resolveSender") CustomSender sender, @Suggest("getShapesWithCustom") String shape) {
        sender.getDelegate().sendMessage("methodcustom=" + shape);
    }

    @Command("greedy")
    public void greedySuggest(CommandSender sender, @Greedy @Suggest("colors") String text) {
        sender.sendMessage("greedy=" + text);
    }

    // Custom sender type
    public static class CustomSender {
        private final String name;
        private final CommandSender delegate;

        public CustomSender(String name, CommandSender delegate) {
            this.name = name;
            this.delegate = delegate;
        }

        public String getName() {
            return name;
        }

        public CommandSender getDelegate() {
            return delegate;
        }
    }
}
