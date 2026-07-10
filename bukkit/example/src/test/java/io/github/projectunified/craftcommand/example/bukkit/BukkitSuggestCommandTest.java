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

    // ═══════════════════════════════════════════════════════════════
    // Signature 1: m(String[] current)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature1_SuggestMethod() {
        PlayerMock player = server.addPlayer();
        List<String> suggestions = server.getCommandMap().getCommand("buksug").tabComplete(player, "buksug", new String[]{"method", ""});
        assertTrue(suggestions.contains("circle"));
        assertTrue(suggestions.contains("square"));
        assertTrue(suggestions.contains("triangle"));
    }

    @Test
    public void testSignature1_SuggestMethodFiltering() {
        PlayerMock player = server.addPlayer();
        List<String> suggestions = server.getCommandMap().getCommand("buksug").tabComplete(player, "buksug", new String[]{"method", "s"});
        assertTrue(suggestions.contains("square"));
        assertFalse(suggestions.contains("circle"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature 3: m(CommandSender sender)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature3_SuggestMethodSender() {
        PlayerMock player = server.addPlayer();
        List<String> suggestions = server.getCommandMap().getCommand("buksug").tabComplete(player, "buksug", new String[]{"methodsender", ""});
        assertTrue(suggestions.contains("circle"));
        assertTrue(suggestions.contains("square"));
        assertTrue(suggestions.contains("triangle"));
    }

    @Test
    public void testSignature3_SuggestMethodSenderExecute() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("buksug").execute(player, "buksug", new String[]{"methodsender", "circle"});
        assertEquals("methodsender=circle", player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Signature 4: m(Player sender, String[] current)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSignature4_SuggestMethodPlayer() {
        PlayerMock player = server.addPlayer();
        List<String> suggestions = server.getCommandMap().getCommand("buksug").tabComplete(player, "buksug", new String[]{"methodplayer", ""});
        assertTrue(suggestions.contains("circle"));
        assertTrue(suggestions.contains("square"));
        assertTrue(suggestions.contains("triangle"));
    }

    @Test
    public void testSignature4_SuggestMethodPlayerExecute() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("buksug").execute(player, "buksug", new String[]{"methodplayer", "triangle"});
        assertEquals("methodplayer=triangle", player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Custom Sender Type: m(CustomSender sender, String[] current)
    // Command method uses @Resolve to get CustomSender
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testCustomSender_SuggestMethodExecute() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("buksug").execute(player, "buksug", new String[]{"methodcustom", "square"});
        assertEquals("methodcustom=square", player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Field Suggest
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testSuggestField() {
        PlayerMock player = server.addPlayer();
        List<String> suggestions = server.getCommandMap().getCommand("buksug").tabComplete(player, "buksug", new String[]{"field", ""});
        assertTrue(suggestions.contains("red"));
        assertTrue(suggestions.contains("green"));
        assertTrue(suggestions.contains("blue"));
    }

    @Test
    public void testSuggestFieldFiltering() {
        PlayerMock player = server.addPlayer();
        List<String> suggestions = server.getCommandMap().getCommand("buksug").tabComplete(player, "buksug", new String[]{"field", "r"});
        assertTrue(suggestions.contains("red"));
        assertFalse(suggestions.contains("green"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Greedy Suggest
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testGreedySuggest() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("buksug").execute(player, "buksug", new String[]{"greedy", "red green"});
        assertEquals("greedy=red green", player.nextMessage());
    }
}
