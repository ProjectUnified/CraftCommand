package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Greedy;
import io.github.projectunified.craftcommand.annotation.Suggest;
import io.papermc.paper.command.brigadier.CommandSourceStack;

import java.util.Arrays;
import java.util.List;

/**
 * Covers @Suggest in Paper context: field, method, @Greedy+@Suggest.
 * Mirror of Bukkit BukkitSuggestCommand.
 */
@Command(value = "psugsug", description = "Paper suggest test commands")
public class PaperSuggestCommand {

    public final List<String> colors = Arrays.asList("red", "green", "blue");

    public List<String> getShapes(CommandSourceStack sender, String[] args, String current) {
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

    @Command("greedy")
    public void greedySuggest(CommandSourceStack sender, @Greedy @Suggest("colors") String text) {
        sender.getSender().sendMessage("greedy=" + text);
    }
}
