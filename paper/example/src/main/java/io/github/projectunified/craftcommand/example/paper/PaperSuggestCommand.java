package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.*;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Covers @Suggest in Paper context: field, method, @Greedy+@Suggest,
 * and suggest methods with different sender types.
 */
@Command(value = "psugsug", description = "Paper suggest test commands")
public class PaperSuggestCommand {

    public final List<String> colors = Arrays.asList("red", "green", "blue");

    // Signature 1: m(String[] current)
    public Collection<String> getShapes(String[] current) {
        return Arrays.asList("circle", "square", "triangle");
    }

    // Signature 3: m(CommandSourceStack sender)
    public Collection<String> getShapesWithCSS(CommandSourceStack sender) {
        return Arrays.asList("circle", "square", "triangle");
    }

    // Signature 4: m(CommandSender sender, String[] current)
    public Collection<String> getShapesWithSender(CommandSender sender, String[] current) {
        return Arrays.asList("circle", "square", "triangle");
    }

    // Signature 4: m(Player sender, String[] current)
    public Collection<String> getShapesWithPlayer(Player sender, String[] current) {
        return Arrays.asList("circle", "square", "triangle");
    }

    // Custom sender resolver
    public CustomSender resolveSender(CommandSourceStack sender) {
        return new CustomSender(sender.getSender().getName(), sender.getSender());
    }

    // Signature 4: m(CustomSender sender, String[] current)
    public Collection<String> getShapesWithCustom(@Resolve("resolveSender") CustomSender sender, String[] current) {
        return Arrays.asList("circle", "square", "triangle");
    }

    @Command("field")
    public void suggestField(CommandSourceStack sender, @Suggest("colors") String color) {
        sender.getSender().sendMessage("field=" + color);
    }

    @Command("method")
    public void suggestMethod(CommandSourceStack sender, @Suggest("getShapes") String shape) {
        sender.getSender().sendMessage("method=" + shape);
    }

    @Command("methodcss")
    public void suggestMethodCSS(CommandSourceStack sender, @Suggest("getShapesWithCSS") String shape) {
        sender.getSender().sendMessage("methodcss=" + shape);
    }

    @Command("methodsender")
    public void suggestMethodSender(CommandSourceStack sender, @Suggest("getShapesWithSender") String shape) {
        sender.getSender().sendMessage("methodsender=" + shape);
    }

    @Command("methodplayer")
    public void suggestMethodPlayer(CommandSourceStack sender, @Suggest("getShapesWithPlayer") String shape) {
        sender.getSender().sendMessage("methodplayer=" + shape);
    }

    @Command("methodcustom")
    public void suggestMethodCustom(@Resolve("resolveSender") CustomSender sender, @Suggest("getShapesWithCustom") String shape) {
        sender.getDelegate().sendMessage("methodcustom=" + shape);
    }

    @Command("greedy")
    public void greedySuggest(CommandSourceStack sender, @Greedy @Suggest("colors") String text) {
        sender.getSender().sendMessage("greedy=" + text);
    }

    @Command("namesug")
    public void nameSuggest(CommandSourceStack sender, @Name("colour") @Suggest("colors") String color) {
        sender.getSender().sendMessage("namesug=" + color);
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
