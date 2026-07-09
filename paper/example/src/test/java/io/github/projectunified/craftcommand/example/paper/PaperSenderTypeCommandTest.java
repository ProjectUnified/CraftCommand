package io.github.projectunified.craftcommand.example.paper;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PaperSenderTypeCommandTest extends AbstractPaperCommandTest {

    @Test
    public void testCssSender() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSenderTypeCommand.class).execute("psendtest css", source(player));
        assertEquals("css=Player0", player.nextMessage());
    }

    @Test
    public void testCsSender() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSenderTypeCommand.class).execute("psendtest cs", source(player));
        assertEquals("cs=Player0", player.nextMessage());
    }

    @Test
    public void testDefaultSender() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSenderTypeCommand.class).execute("psendtest default", source(player));
        assertEquals("default=Player0", player.nextMessage());
    }
}
