package io.github.projectunified.craftcommand.example.paper;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PaperResolveCommandTest extends AbstractPaperCommandTest {

    @Test
    public void testNamedResolve() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperResolveCommand.class).execute("presolve named", source(player));
        assertEquals("named=Player0", player.nextMessage());
    }

    @Test
    public void testResolveDefault() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperResolveCommand.class).execute("presolve def", source(player));
        assertEquals("def=Player0", player.nextMessage());
    }
}
