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

public class PaperEnumCommandTest extends AbstractPaperCommandTest {
    @Test
    public void testEnumArgument() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperEnumCommand.class).execute("penum mode FAST", source(player));
        assertEquals("mode=FAST", player.nextMessage());
    }

    @Test
    public void testEnumDefault() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperEnumCommand.class);
        dispatcher.execute("penum optional", source(player));
        assertEquals("optional=FAST", player.nextMessage());
        dispatcher.execute("penum optional SLOW", source(player));
        assertEquals("optional=SLOW", player.nextMessage());
    }

    @Test
    public void testEnumSuggestions() throws Exception {
        PlayerMock player = server.addPlayer();
        CommandDispatcher<CommandSourceStack> dispatcher = register(PaperEnumCommand.class);
        ParseResults<CommandSourceStack> parse = dispatcher.parse("penum mode ", source(player));
        Suggestions result = dispatcher.getCompletionSuggestions(parse).get();
        List<String> suggestions = result.getList().stream()
                .map(Suggestion::getText)
                .collect(Collectors.toList());
        assertTrue(suggestions.contains("FAST"), suggestions.toString());
        assertTrue(suggestions.contains("SLOW"), suggestions.toString());
    }
}
