package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.bukkit.BukkitCommandManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class NestedPermissionCommandTest extends AbstractBukkitCommandTest {
    @Override
    protected void registerCommand() {
        new BukkitCommandManager(MockBukkit.createMockPlugin()).register(new NestedPermissionCommand());
    }

    @Test
    public void testNestedEmptyPermission() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("nestedperm").execute(player, "nestedperm", new String[]{"empty"});
        assertNotNull(player.nextMessage());
    }
}
