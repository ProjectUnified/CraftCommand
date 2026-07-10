package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.annotation.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Covers Suggest combinations:
 * field, method, Greedy+Suggest, Name+Suggest, Default+Suggest,
 * Resolve+Suggest (on resolver param), Greedy+Name+Suggest.
 */
@Command(value = "suggest", description = "Suggest annotation combinations")
public class SuggestCommand {

    public final List<String> colors = Arrays.asList("red", "green", "blue");

    public Collection<String> getShapes(String[] current) {
        return Arrays.asList("circle", "square", "triangle");
    }

    public CustomColor resolveColor(String name) {
        return new CustomColor(name);
    }

    @Command("field")
    public void suggestField(Object sender, @Suggest("colors") String color) {
        ((TestSender) sender).sendMessage("color=" + color);
    }

    @Command("method")
    public void suggestMethod(Object sender, @Suggest("getShapes") String shape) {
        ((TestSender) sender).sendMessage("shape=" + shape);
    }

    @Command("greedysug")
    public void greedySuggest(Object sender, @Greedy @Suggest("colors") String text) {
        ((TestSender) sender).sendMessage("greedysug=" + text);
    }

    @Command("namesug")
    public void nameSuggest(Object sender, @Name("colour") @Suggest("colors") String color) {
        ((TestSender) sender).sendMessage("namesug=" + color);
    }

    @Command("defaultsug")
    public void defaultSuggest(Object sender, @Default("red") @Suggest("colors") String color) {
        ((TestSender) sender).sendMessage("defaultsug=" + color);
    }

    @Command("resolvesug")
    public void resolveSuggest(Object sender, @Resolve("resolveColor") CustomColor color) {
        ((TestSender) sender).sendMessage("resolvesug=" + color.name);
    }

    @Command("greedyname")
    public void greedyNameSuggest(Object sender, @Greedy @Name("text") @Suggest("colors") String text) {
        ((TestSender) sender).sendMessage("greedyname=" + text);
    }

    public static class CustomColor {
        public final String name;

        public CustomColor(String name) {
            this.name = name;
        }
    }
}
