package io.github.projectunified.craftcommand.example.standalone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.projectunified.craftcommand.example.standalone.TestHelpers.assertSuggestionsContain;
import static org.junit.jupiter.api.Assertions.*;

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
        List<String> suggestions = tabComplete("field", "");
        assertSuggestionsContain(suggestions, "red", "green", "blue");
    }

    @Test
    public void testSuggestMethod() {
        List<String> suggestions = tabComplete("method", "");
        assertSuggestionsContain(suggestions, "circle", "square", "triangle");
    }

    @Test
    public void testGreedySuggest() {
        assertTrue(execute("greedysug", "red green"));
        assertEquals(List.of("greedysug=red green"), sender.getMessages());
    }

    @Test
    public void testNameSuggest() {
        assertTrue(execute("namesug", "blue"));
        assertEquals(List.of("namesug=blue"), sender.getMessages());
    }

    @Test
    public void testDefaultSuggest() {
        assertTrue(execute("defaultsug"));
        assertEquals(List.of("defaultsug=red"), sender.getMessages());
        sender.getMessages().clear();
        assertTrue(execute("defaultsug", "green"));
        assertEquals(List.of("defaultsug=green"), sender.getMessages());
    }

    @Test
    public void testResolveSuggest() {
        assertTrue(execute("resolvesug", "myColor"));
        assertEquals(List.of("resolvesug=myColor"), sender.getMessages());
    }

    @Test
    public void testGreedyNameSuggest() {
        assertTrue(execute("greedyname", "red green blue"));
        assertEquals(List.of("greedyname=red green blue"), sender.getMessages());
    }

    @Test
    public void testSuggestFieldTabComplete() {
        List<String> suggestions = tabComplete("field", "r");
        assertTrue(suggestions.contains("red"));
        assertFalse(suggestions.contains("green"));
    }

    @Test
    public void testSuggestMethodTabComplete() {
        List<String> suggestions = tabComplete("method", "s");
        assertTrue(suggestions.contains("square"));
        assertFalse(suggestions.contains("circle"));
    }
}
