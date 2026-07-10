package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.bukkit.BukkitCommandManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class BukkitSuggestCommandTest extends AbstractBukkitCommandTest {

    @Override
    protected void registerCommand() {
        new BukkitCommandManager(MockBukkit.createMockPlugin()).register(new BukkitSuggestCommand());
    }

    @Test
    public void testSuggestField() {
        PlayerMock player = server.addPlayer();
        List<String> suggestions = server.getCommandMap().getCommand("buksug").tabComplete(player, "buksug", new String[]{"field", ""});
        assertTrue(suggestions.contains("red"));
        assertTrue(suggestions.contains("green"));
        assertTrue(suggestions.contains("blue"));
    }

    @Test
    public void testSuggestMethod() {
        PlayerMock player = server.addPlayer();
        List<String> suggestions = server.getCommandMap().getCommand("buksug").tabComplete(player, "buksug", new String[]{"method", ""});
        assertTrue(suggestions.contains("circle"));
        assertTrue(suggestions.contains("square"));
        assertTrue(suggestions.contains("triangle"));
    }

    @Test
    public void testGreedySuggest() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("buksug").execute(player, "buksug", new String[]{"greedy", "red green"});
        assertEquals("greedy=red green", player.nextMessage());
    }

    @Test
    public void testSuggestFieldFiltering() {
        PlayerMock player = server.addPlayer();
        List<String> suggestions = server.getCommandMap().getCommand("buksug").tabComplete(player, "buksug", new String[]{"field", "r"});
        assertTrue(suggestions.contains("red"));
        assertFalse(suggestions.contains("green"));
    }

    @Test
    public void testSuggestMethodFiltering() {
        PlayerMock player = server.addPlayer();
        List<String> suggestions = server.getCommandMap().getCommand("buksug").tabComplete(player, "buksug", new String[]{"method", "s"});
        assertTrue(suggestions.contains("square"));
        assertFalse(suggestions.contains("circle"));
    }

    @Test
    public void testSuggestFieldComplete() {
        PlayerMock player = server.addPlayer();
        List<String> suggestions = server.getCommandMap().getCommand("buksug").tabComplete(player, "buksug", new String[]{"field", ""});
        assertEquals(3, suggestions.size());
        assertTrue(suggestions.contains("red"));
        assertTrue(suggestions.contains("green"));
        assertTrue(suggestions.contains("blue"));
    }
}
