package io.github.projectunified.craftcommand.example.paper;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class PaperResolveCommandTest extends AbstractPaperCommandTest {

    @Test
    public void testNamedResolve() throws Exception {
        PlayerMock player = server.addPlayer("TestPlayer0");
        register(PaperResolveCommand.class).execute("paperresolve named", source(player));
        assertEquals("named=TestPlayer0", player.nextMessage());
    }

    @Test
    public void testStringResolve() throws Exception {
        PlayerMock player = server.addPlayer("TestPlayer0");
        register(PaperResolveCommand.class).execute("paperresolve string hello", source(player));
        assertEquals("string=hello", player.nextMessage());
    }

    @Test
    public void testStringWithDefault() throws Exception {
        PlayerMock player = server.addPlayer("TestPlayer0");
        register(PaperResolveCommand.class).execute("paperresolve stringWithDefault world", source(player));
        assertEquals("stringWithDefault=world,name=TestPlayer0", player.nextMessage());
    }

    @Test
    public void testDefaultResolve() throws Exception {
        PlayerMock player = server.addPlayer("TestPlayer0");
        register(PaperResolveCommand.class).execute("paperresolve def", source(player));
        assertEquals("def=def", player.nextMessage());
    }

    @Test
    public void testDefaultResolveWithValue() throws Exception {
        PlayerMock player = server.addPlayer("TestPlayer0");
        register(PaperResolveCommand.class).execute("paperresolve def custom", source(player));
        assertEquals("def=custom", player.nextMessage());
    }
}
