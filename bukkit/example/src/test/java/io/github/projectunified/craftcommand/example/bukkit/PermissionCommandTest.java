package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.bukkit.BukkitCommandManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

public class PermissionCommandTest extends AbstractBukkitCommandTest {

    @Override
    protected void registerCommand() {
        new BukkitCommandManager(MockBukkit.createMockPlugin()).register(new PermissionCommand());
    }

    private PlayerMock addPlayer(String... perms) {
        PlayerMock player = server.addPlayer();
        for (String perm : perms) {
            player.addAttachment(MockBukkit.createMockPlugin(), perm, true);
        }
        return player;
    }

    @Test
    public void testAllowed() {
        PlayerMock player = addPlayer("example.perm");
        server.getCommandMap().getCommand("permtest").execute(player, "permtest", new String[]{"allowed"});
        assertEquals("allowed", player.nextMessage());
    }

    @Test
    public void testAllowedDenied() {
        PlayerMock player = addPlayer();
        server.getCommandMap().getCommand("permtest").execute(player, "permtest", new String[]{"allowed"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testMethodPerm() {
        PlayerMock player = addPlayer("example.perm.admin");
        server.getCommandMap().getCommand("permtest").execute(player, "permtest", new String[]{"methodperm"});
        assertEquals("methodperm", player.nextMessage());
    }

    @Test
    public void testMethodPermDenied() {
        PlayerMock player = addPlayer("example.perm");
        server.getCommandMap().getCommand("permtest").execute(player, "permtest", new String[]{"methodperm"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testCustomMsg() {
        PlayerMock player = addPlayer();
        server.getCommandMap().getCommand("permtest").execute(player, "permtest", new String[]{"custommsg"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testOverride() {
        PlayerMock player = addPlayer("example.perm.override");
        server.getCommandMap().getCommand("permtest").execute(player, "permtest", new String[]{"override"});
        assertEquals("override", player.nextMessage());
    }
}
