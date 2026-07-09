package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.annotation.*;

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
        System.out.println("greedyempty='" + text + "'");
    }

    @Command("greedydef")
    public void greedyDefault(Object sender, @Greedy @Default("fallback") String text) {
        System.out.println("greedydef='" + text + "'");
    }

    @Command("enum")
    public void enumParam(Object sender, Color color) {
        System.out.println("enum=" + color);
    }

    @Command("desc")
    public void descriptionCheck(Object sender) {
        System.out.println("desc");
    }

    @Command("defname")
    public void defaultName(Object sender, @Default("hello") @Name("greeting") String text) {
        System.out.println("defname=" + text);
    }

    @Command(value = "multi", aliases = {"m", "multiple"})
    public void multiAlias(Object sender, String value) {
        System.out.println("multi=" + value);
    }

    public enum Color {
        RED, GREEN, BLUE
    }
}
