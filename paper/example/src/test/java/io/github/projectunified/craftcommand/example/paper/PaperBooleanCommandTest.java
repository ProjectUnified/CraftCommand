package io.github.projectunified.craftcommand.example.paper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.ParseResults;
import com.mojang.brigadier.suggestion.Suggestion;
import com.mojang.brigadier.suggestion.Suggestions;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class PaperBooleanCommandTest extends AbstractPaperCommandTest {

    @Test
    public void testExecute() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperBooleanCommand.class).execute("pbool flag true", source(player));
        assertEquals("flag=true", player.nextMessage());
    }

    @Test
    public void testResolvedSenderExecute() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperBooleanCommand.class).execute("pbool resolved true", source(player));
        assertEquals("resolved=true", player.nextMessage());
    }

    @Test
    public void testResolvedSenderSuggestions() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperBooleanCommand.class);

        ParseResults<CommandSourceStack> parse = dispatcher.parse("pbool resolved ", source(player));
        Suggestions result = dispatcher.getCompletionSuggestions(parse).get();
        List<String> suggestions = result.getList().stream()
                .map(Suggestion::getText)
                .collect(Collectors.toList());

        assertTrue(suggestions.contains("true"), "Expected true in " + suggestions);
        assertTrue(suggestions.contains("false"), "Expected false in " + suggestions);
    }

    @Test
    public void testSuggestions() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperBooleanCommand.class);

        ParseResults<CommandSourceStack> parse = dispatcher.parse("pbool flag ", source(player));
        Suggestions result = dispatcher.getCompletionSuggestions(parse).get();
        List<String> suggestions = result.getList().stream()
                .map(Suggestion::getText)
                .collect(Collectors.toList());

        assertTrue(suggestions.contains("true"), "Expected true in " + suggestions);
        assertTrue(suggestions.contains("false"), "Expected false in " + suggestions);
    }
}
