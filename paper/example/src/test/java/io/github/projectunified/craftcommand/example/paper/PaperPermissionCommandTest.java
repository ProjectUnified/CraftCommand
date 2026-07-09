package io.github.projectunified.craftcommand.example.paper;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

public class PaperPermissionCommandTest extends AbstractPaperCommandTest {

    @Test
    public void testAllowed() throws Exception {
        PlayerMock player = server.addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "example.perm", true);
        register(PaperPermissionCommand.class).execute("ppermtest allowed", source(player));
        assertEquals("allowed", player.nextMessage());
    }

    @Test
    public void testAllowedDenied() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperPermissionCommand.class).execute("ppermtest allowed", source(player));
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testMethodPerm() throws Exception {
        PlayerMock player = server.addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "example.perm.admin", true);
        register(PaperPermissionCommand.class).execute("ppermtest methodperm", source(player));
        assertEquals("methodperm", player.nextMessage());
    }

    @Test
    public void testMethodPermDenied() throws Exception {
        PlayerMock player = server.addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "example.perm", true);
        register(PaperPermissionCommand.class).execute("ppermtest methodperm", source(player));
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testCustomMsg() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperPermissionCommand.class).execute("ppermtest custommsg", source(player));
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testOverride() throws Exception {
        PlayerMock player = server.addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "example.perm.override", true);
        register(PaperPermissionCommand.class).execute("ppermtest override", source(player));
        assertEquals("override", player.nextMessage());
    }
}
