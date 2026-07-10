package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.bukkit.BukkitCommandManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

public class BukkitResolveCommandTest extends AbstractBukkitCommandTest {

    @Override
    protected void registerCommand() {
        new BukkitCommandManager(MockBukkit.createMockPlugin()).register(new BukkitResolveCommand());
    }

    @Test
    public void testNamedResolve() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("bukkitresolve").execute(player, "bukkitresolve", new String[]{"named"});
        String message = player.nextMessage();
        assertNotNull(message);
        assertTrue(message.startsWith("named="));
    }

    @Test
    public void testStringResolve() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("bukkitresolve").execute(player, "bukkitresolve", new String[]{"string", "hello"});
        assertEquals("string=hello", player.nextMessage());
    }

    @Test
    public void testStringWithDefault() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("bukkitresolve").execute(player, "bukkitresolve", new String[]{"stringWithDefault", "world"});
        String message = player.nextMessage();
        assertNotNull(message);
        assertTrue(message.startsWith("stringWithDefault=world,name="));
    }

    @Test
    public void testDefaultResolve() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("bukkitresolve").execute(player, "bukkitresolve", new String[]{"def"});
        assertEquals("def=def", player.nextMessage());
    }

    @Test
    public void testDefaultResolveWithValue() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("bukkitresolve").execute(player, "bukkitresolve", new String[]{"def", "custom"});
        assertEquals("def=custom", player.nextMessage());
    }
}
