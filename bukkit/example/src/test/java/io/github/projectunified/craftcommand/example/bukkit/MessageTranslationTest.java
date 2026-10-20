package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.bukkit.BukkitCommandManager;
import org.bukkit.plugin.java.JavaPlugin;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class MessageTranslationTest extends AbstractBukkitCommandTest {
    private BukkitCommandManager manager;

    @Override
    protected void registerCommand() {
        manager = new TranslatingManager(MockBukkit.createMockPlugin());
        manager.register(new AllFeaturesCommand());
    }

    @Test
    public void testPermissionMessageTranslation() {
        PlayerMock player = server.addPlayer();
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"hello", "world"});
        assertEquals("Denied: features.base", player.nextMessage());
    }

    @Test
    public void testExceptionMessageTranslation() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "features.base", true);
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"add", "10"});
        assertEquals("Error: Usage: <a> <b>", player.nextMessage());
    }

    private static final class TranslatingManager extends BukkitCommandManager {
        private TranslatingManager(JavaPlugin plugin) {
            super(plugin);
        }

        @Override
        public String formatMessage(String key, String defaultValue, Object... args) {
            if (key.equals("error")) return "Error: " + args[0];
            if (key.equals("permission")) return "Denied: " + args[0];
            return super.formatMessage(key, defaultValue, args);
        }
    }
}
