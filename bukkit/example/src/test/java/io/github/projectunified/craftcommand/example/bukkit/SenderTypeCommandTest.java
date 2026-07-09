package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.bukkit.BukkitCommandManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class SenderTypeCommandTest extends AbstractBukkitCommandTest {

    @Override
    protected void registerCommand() {
        new BukkitCommandManager(MockBukkit.createMockPlugin()).register(new SenderTypeCommand());
    }

    @Test
    public void testPlayerSender() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("sendtest").execute(player, "sendtest", new String[]{"player"});
        assertEquals("player=Player0", player.nextMessage());
    }

    @Test
    public void testCommandSenderSender() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("sendtest").execute(player, "sendtest", new String[]{"commandsender"});
        assertEquals("commandsender=Player0", player.nextMessage());
    }

    @Test
    public void testDefaultSender() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("sendtest").execute(player, "sendtest", new String[]{"default"});
        assertEquals("default=Player0", player.nextMessage());
    }
}
