package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.annotation.*;

import java.util.Arrays;
import java.util.Collection;
import java.util.List;

/**
 * Covers Suggest combinations:
 * field, method, Greedy+Suggest, Name+Suggest, Default+Suggest,
 * Resolve+Suggest (on resolver param), Greedy+Name+Suggest.
 * <p>
 * Suggest method signatures:
 * 1. m(String[] current)
 * 2. m(String[] current, String[] context)
 * 3. m(SenderType sender)
 * 4. m(SenderType sender, String[] current)
 * 5. m(SenderType sender, String[] current, String[] context)
 */
@Command(value = "suggest", description = "Suggest annotation combinations")
public class SuggestCommand {

    public final List<String> colors = Arrays.asList("red", "green", "blue");

    // Signature 1: m(String[] current)
    public Collection<String> getShapes(String[] current) {
        return Arrays.asList("circle", "square", "triangle");
    }

    // Signature 2: m(String[] current, String[] context)
    public Collection<String> getShapesWithContext(String[] current, String[] context) {
        return Arrays.asList("circle", "square", "triangle");
    }

    // Signature 3: m(SenderType sender)
    public Collection<String> getShapesForSender(Object sender) {
        return Arrays.asList("circle", "square", "triangle");
    }

    // Signature 4: m(SenderType sender, String[] current)
    public Collection<String> getShapesWithSender(Object sender, String[] current) {
        return Arrays.asList("circle", "square", "triangle");
    }

    // Signature 5: m(SenderType sender, String[] current, String[] context)
    public Collection<String> getShapesFull(Object sender, String[] current, String[] context) {
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

    @Command("methodcontext")
    public void suggestMethodContext(Object sender, @Suggest("getShapesWithContext") String shape) {
        ((TestSender) sender).sendMessage("shapecontext=" + shape);
    }

    @Command("methodsender")
    public void suggestMethodSender(Object sender, @Suggest("getShapesForSender") String shape) {
        ((TestSender) sender).sendMessage("shapesender=" + shape);
    }

    @Command("methodsendercurrent")
    public void suggestMethodSenderCurrent(Object sender, @Suggest("getShapesWithSender") String shape) {
        ((TestSender) sender).sendMessage("shapesendercurrent=" + shape);
    }

    @Command("methodfull")
    public void suggestMethodFull(Object sender, @Suggest("getShapesFull") String shape) {
        ((TestSender) sender).sendMessage("shapefull=" + shape);
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
