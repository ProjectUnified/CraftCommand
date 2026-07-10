package io.github.projectunified.craftcommand.example.paper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class PaperSuggestCommandTest extends AbstractPaperCommandTest {

    private List<String> getSuggestions(CommandDispatcher<CommandSourceStack> dispatcher, CommandSourceStack source, String input) throws Exception {
        ParseResults<CommandSourceStack> parse = dispatcher.parse(input, source);
        CompletableFuture<Suggestions> suggestions = dispatcher.getCompletionSuggestions(parse);
        Suggestions result = suggestions.get();
        return result.getList().stream()
                .map(Suggestion::getText)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature 1: m(String[] current)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature1_MethodSuggestions() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperSuggestCommand.class);
        List<String> suggestions = getSuggestions(dispatcher, source(player), "psugsug method ");
        assertTrue(suggestions.contains("circle"));
        assertTrue(suggestions.contains("square"));
        assertTrue(suggestions.contains("triangle"));
    }

    @Test
    public void testSignature1_MethodFiltering() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperSuggestCommand.class);
        List<String> suggestions = getSuggestions(dispatcher, source(player), "psugsug method s");
        assertTrue(suggestions.contains("square"));
        assertFalse(suggestions.contains("circle"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature 3: m(CommandSourceStack sender)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature3_MethodCSSSuggestions() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperSuggestCommand.class);
        List<String> suggestions = getSuggestions(dispatcher, source(player), "psugsug methodcss ");
        assertTrue(suggestions.contains("circle"));
        assertTrue(suggestions.contains("square"));
        assertTrue(suggestions.contains("triangle"));
    }

    @Test
    public void testSignature3_MethodCSSExecute() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSuggestCommand.class).execute("psugsug methodcss square", source(player));
        assertEquals("methodcss=square", player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature 4: m(CommandSender sender, String[] current)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature4_MethodSenderSuggestions() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperSuggestCommand.class);
        List<String> suggestions = getSuggestions(dispatcher, source(player), "psugsug methodsender ");
        assertTrue(suggestions.contains("circle"));
        assertTrue(suggestions.contains("square"));
        assertTrue(suggestions.contains("triangle"));
    }

    @Test
    public void testSignature4_MethodSenderExecute() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSuggestCommand.class).execute("psugsug methodsender triangle", source(player));
        assertEquals("methodsender=triangle", player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature 4: m(Player sender, String[] current)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature4_MethodPlayerSuggestions() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperSuggestCommand.class);
        List<String> suggestions = getSuggestions(dispatcher, source(player), "psugsug methodplayer ");
        assertTrue(suggestions.contains("circle"));
        assertTrue(suggestions.contains("square"));
        assertTrue(suggestions.contains("triangle"));
    }

    @Test
    public void testSignature4_MethodPlayerExecute() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSuggestCommand.class).execute("psugsug methodplayer circle", source(player));
        assertEquals("methodplayer=circle", player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Custom Sender Type: m(CustomSender sender, String[] current)
    // Command method uses @Resolve to get CustomSender
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testCustomSender_MethodExecute() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSuggestCommand.class).execute("psugsug methodcustom square", source(player));
        assertEquals("methodcustom=square", player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Field Suggest
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSuggestFieldSuggestions() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperSuggestCommand.class);
        List<String> suggestions = getSuggestions(dispatcher, source(player), "psugsug field ");
        assertTrue(suggestions.contains("red"));
        assertTrue(suggestions.contains("green"));
        assertTrue(suggestions.contains("blue"));
    }

    @Test
    public void testSuggestFieldFiltering() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperSuggestCommand.class);
        List<String> suggestions = getSuggestions(dispatcher, source(player), "psugsug field r");
        assertTrue(suggestions.contains("red"));
        assertFalse(suggestions.contains("green"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Name Suggest
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testNameSuggest() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSuggestCommand.class).execute("psugsug namesug blue", source(player));
        assertEquals("namesug=blue", player.nextMessage());
    }

    @Test
    public void testNameSuggestSuggestions() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperSuggestCommand.class);
        List<String> suggestions = getSuggestions(dispatcher, source(player), "psugsug namesug ");
        assertTrue(suggestions.contains("red"));
        assertTrue(suggestions.contains("green"));
        assertTrue(suggestions.contains("blue"));
    }
}
