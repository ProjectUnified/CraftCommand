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

    @Test
    public void testSuggestField() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSuggestCommand.class).execute("psugsug field red", source(player));
        assertEquals("field=red", player.nextMessage());
    }

    @Test
    public void testSuggestMethod() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSuggestCommand.class).execute("psugsug method circle", source(player));
        assertEquals("method=circle", player.nextMessage());
    }

    @Test
    public void testGreedySuggest() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSuggestCommand.class).execute("psugsug greedy red green", source(player));
        assertEquals("greedy=red green", player.nextMessage());
    }

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
    public void testSuggestMethodSuggestions() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperSuggestCommand.class);
        List<String> suggestions = getSuggestions(dispatcher, source(player), "psugsug method ");
        assertTrue(suggestions.contains("circle"));
        assertTrue(suggestions.contains("square"));
        assertTrue(suggestions.contains("triangle"));
    }

    @Test
    public void testSuggestFieldFiltering() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperSuggestCommand.class);
        List<String> suggestions = getSuggestions(dispatcher, source(player), "psugsug field r");
        assertTrue(suggestions.contains("red"));
        assertFalse(suggestions.contains("green"));
    }

    @Test
    public void testSuggestMethodFiltering() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperSuggestCommand.class);
        List<String> suggestions = getSuggestions(dispatcher, source(player), "psugsug method s");
        assertTrue(suggestions.contains("square"));
        assertFalse(suggestions.contains("circle"));
    }
}
