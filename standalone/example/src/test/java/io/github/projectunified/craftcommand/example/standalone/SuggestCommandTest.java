package io.github.projectunified.craftcommand.example.standalone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.projectunified.craftcommand.example.standalone.TestHelpers.assertSuggestionsContain;

public class SuggestCommandTest extends AbstractStandaloneCommandTest {

    @Override
    protected void registerCommand() {
        manager.register(new SuggestCommand());
    }

    @Override
    protected String getCommandName() {
        return "suggest";
    }

    @Test
    public void testSuggestField() {
        List<String> suggestions = cmd.tabComplete("sender", new String[]{"field", ""});
        assertSuggestionsContain(suggestions, "red", "green", "blue");
    }

    @Test
    public void testSuggestMethod() {
        List<String> suggestions = cmd.tabComplete("sender", new String[]{"method", ""});
        assertSuggestionsContain(suggestions, "circle", "square", "triangle");
    }

    @Test
    public void testGreedySuggest() {
        assertTrue(cmd.execute("sender", new String[]{"greedysug", "red green"}));
    }

    @Test
    public void testNameSuggest() {
        assertTrue(cmd.execute("sender", new String[]{"namesug", "blue"}));
    }

    @Test
    public void testDefaultSuggest() {
        assertTrue(cmd.execute("sender", new String[]{"defaultsug"}));
        assertTrue(cmd.execute("sender", new String[]{"defaultsug", "green"}));
    }

    @Test
    public void testResolveSuggest() {
        assertTrue(cmd.execute("sender", new String[]{"resolvesug", "myColor"}));
    }

    @Test
    public void testGreedyNameSuggest() {
        assertTrue(cmd.execute("sender", new String[]{"greedyname", "red green blue"}));
    }

    @Test
    public void testSuggestFieldTabComplete() {
        List<String> suggestions = cmd.tabComplete("sender", new String[]{"field", "r"});
        assertTrue(suggestions.contains("red"));
        assertFalse(suggestions.contains("green"));
    }

    @Test
    public void testSuggestMethodTabComplete() {
        List<String> suggestions = cmd.tabComplete("sender", new String[]{"method", "s"});
        assertTrue(suggestions.contains("square"));
        assertFalse(suggestions.contains("circle"));
    }
}
