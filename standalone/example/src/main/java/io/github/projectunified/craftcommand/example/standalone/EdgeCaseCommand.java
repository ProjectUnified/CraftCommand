package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Default;
import io.github.projectunified.craftcommand.annotation.Greedy;
import io.github.projectunified.craftcommand.annotation.Name;

import java.util.Arrays;
import java.util.List;

/**
 * Covers edge cases:
 * Greedy zero args, Greedy+Default, enum param, description assertion,
 * Default+Name, multiple aliases.
 */
@Command(value = "edge", aliases = {"e", "edges"}, description = "Edge case combinations")
public class EdgeCaseCommand {

    public final List<String> items = Arrays.asList("a", "b", "c");

    @Command("greedyempty")
    public void greedyEmpty(Object sender, @Greedy String text) {
        ((TestSender) sender).sendMessage("greedyempty='" + text + "'");
    }

    @Command("greedydef")
    public void greedyDefault(Object sender, @Greedy @Default("fallback") String text) {
        ((TestSender) sender).sendMessage("greedydef='" + text + "'");
    }

    @Command("enum")
    public void enumParam(Object sender, Color color) {
        ((TestSender) sender).sendMessage("enum=" + color);
    }

    @Command("desc")
    public void descriptionCheck(Object sender) {
        ((TestSender) sender).sendMessage("desc");
    }

    @Command("defname")
    public void defaultName(Object sender, @Default("hello") @Name("greeting") String text) {
        ((TestSender) sender).sendMessage("defname=" + text);
    }

    @Command(value = "multi", aliases = {"m", "multiple"})
    public void multiAlias(Object sender, String value) {
        ((TestSender) sender).sendMessage("multi=" + value);
    }

    public enum Color {
        RED, GREEN, BLUE
    }
}
