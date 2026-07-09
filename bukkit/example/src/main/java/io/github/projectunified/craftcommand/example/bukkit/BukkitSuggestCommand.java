package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Greedy;
import io.github.projectunified.craftcommand.annotation.Suggest;
import org.bukkit.command.CommandSender;

import java.util.Arrays;
import java.util.List;

/**
 * Covers @Suggest in Bukkit context: field, method, @Greedy+@Suggest.
 */
@Command(value = "buksug", description = "Bukkit suggest test commands")
public class BukkitSuggestCommand {

    public final List<String> colors = Arrays.asList("red", "green", "blue");

    public List<String> getShapes(CommandSender sender, String[] args, String current) {
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

    @Command("greedy")
    public void greedySuggest(CommandSender sender, @Greedy @Suggest("colors") String text) {
        sender.sendMessage("greedy=" + text);
    }
}
