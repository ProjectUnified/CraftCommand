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

    // ═══════════════════════════════════════════════════════════════
    // Signature 0: m()
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature0_SuggestMethodNoArg() {
        List<String> suggestions = tabComplete("methodnoarg", "");
        assertSuggestionsContain(suggestions, "circle", "square", "triangle");
    }

    @Test
    public void testSignature0_SuggestMethodNoArgFiltering() {
        List<String> suggestions = tabComplete("methodnoarg", "s");
        assertTrue(suggestions.contains("square"));
        assertFalse(suggestions.contains("circle"));
    }

    @Test
    public void testSignature0_SuggestMethodNoArgExecute() {
        assertTrue(execute("methodnoarg", "triangle"));
        assertEquals("shapenoarg=triangle", sender.getMessages().get(0));
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature 1: m(String[] current)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature1_SuggestMethod() {
        List<String> suggestions = tabComplete("method", "");
        assertSuggestionsContain(suggestions, "circle", "square", "triangle");
    }

    @Test
    public void testSignature1_SuggestMethodFiltering() {
        List<String> suggestions = tabComplete("method", "s");
        assertTrue(suggestions.contains("square"));
        assertFalse(suggestions.contains("circle"));
    }

    @Test
    public void testSignature1_SuggestMethodExecute() {
        assertTrue(execute("method", "circle"));
        assertEquals("shape=circle", sender.getMessages().get(0));
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature 2: m(String[] current, String[] context)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature2_SuggestMethodContext() {
        List<String> suggestions = tabComplete("methodcontext", "");
        assertSuggestionsContain(suggestions, "circle", "square", "triangle");
    }

    @Test
    public void testSignature2_SuggestMethodContextFiltering() {
        List<String> suggestions = tabComplete("methodcontext", "c");
        assertTrue(suggestions.contains("circle"));
        assertFalse(suggestions.contains("square"));
    }

    @Test
    public void testSignature2_SuggestMethodContextExecute() {
        assertTrue(execute("methodcontext", "square"));
        assertEquals("shapecontext=square", sender.getMessages().get(0));
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature 3: m(SenderType sender)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature3_SuggestMethodSender() {
        List<String> suggestions = tabComplete("methodsender", "");
        assertSuggestionsContain(suggestions, "circle", "square", "triangle");
    }

    @Test
    public void testSignature3_SuggestMethodSenderFiltering() {
        List<String> suggestions = tabComplete("methodsender", "t");
        assertTrue(suggestions.contains("triangle"));
        assertFalse(suggestions.contains("circle"));
    }

    @Test
    public void testSignature3_SuggestMethodSenderExecute() {
        assertTrue(execute("methodsender", "triangle"));
        assertEquals("shapesender=triangle", sender.getMessages().get(0));
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature 4: m(SenderType sender, String[] current)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature4_SuggestMethodSenderCurrent() {
        List<String> suggestions = tabComplete("methodsendercurrent", "");
        assertSuggestionsContain(suggestions, "circle", "square", "triangle");
    }

    @Test
    public void testSignature4_SuggestMethodSenderCurrentFiltering() {
        List<String> suggestions = tabComplete("methodsendercurrent", "c");
        assertTrue(suggestions.contains("circle"));
        assertFalse(suggestions.contains("square"));
    }

    @Test
    public void testSignature4_SuggestMethodSenderCurrentExecute() {
        assertTrue(execute("methodsendercurrent", "circle"));
        assertEquals("shapesendercurrent=circle", sender.getMessages().get(0));
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature 5: m(SenderType sender, String[] current, String[] context)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature5_SuggestMethodFull() {
        List<String> suggestions = tabComplete("methodfull", "");
        assertSuggestionsContain(suggestions, "circle", "square", "triangle");
    }

    @Test
    public void testSignature5_SuggestMethodFullFiltering() {
        List<String> suggestions = tabComplete("methodfull", "s");
        assertTrue(suggestions.contains("square"));
        assertFalse(suggestions.contains("triangle"));
    }

    @Test
    public void testSignature5_SuggestMethodFullExecute() {
        assertTrue(execute("methodfull", "square"));
        assertEquals("shapefull=square", sender.getMessages().get(0));
    }

    // ═══════════════════════════════════════════════════════════════
    // Field Suggest
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSuggestField() {
        List<String> suggestions = tabComplete("field", "");
        assertSuggestionsContain(suggestions, "red", "green", "blue");
    }

    @Test
    public void testSuggestFieldFiltering() {
        List<String> suggestions = tabComplete("field", "r");
        assertTrue(suggestions.contains("red"));
        assertFalse(suggestions.contains("green"));
    }

    @Test
    public void testSuggestFieldEmptyInput() {
        List<String> suggestions = tabComplete("field", "");
        assertEquals(3, suggestions.size());
    }

    @Test
    public void testSuggestFieldNoMatch() {
        List<String> suggestions = tabComplete("field", "xyz");
        assertTrue(suggestions.isEmpty());
    }

    // ═══════════════════════════════════════════════════════════════
    // Other Suggest Features
    // ═══════════════════════════════════════════════════════════════

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

    // ═══════════════════════════════════════════════════════════════
    // Tab Completion Subcommands
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testTabCompletionSubcommands() {
        List<String> suggestions = tabComplete("");
        assertSuggestionsContain(suggestions, "field", "method", "methodnoarg", "methodcontext", "methodsender",
                "methodsendercurrent", "methodfull", "greedysug", "namesug", "defaultsug",
                "resolvesug", "greedyname");
    }
}
