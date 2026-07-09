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
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testResolveDefault() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("bukkitresolve").execute(player, "bukkitresolve", new String[]{"def"});
        assertNotNull(player.nextMessage());
    }
}
