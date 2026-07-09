package io.github.projectunified.craftcommand.example.paper;

import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import static org.junit.jupiter.api.Assertions.*;

public class PaperSuggestCommandTest extends AbstractPaperCommandTest {

    @Test
    public void testSuggestField() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSuggestCommand.class).execute("psugsug field red", source(player));
        assertEquals("field=red", player.nextMessage());
    }

    @Test
    public void testSuggestMethod() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSuggestCommand.class).execute("psugsug method circle", source(player));
        assertEquals("method=circle", player.nextMessage());
    }

    @Test
    public void testGreedySuggest() throws Exception {
        PlayerMock player = server.addPlayer();
        register(PaperSuggestCommand.class).execute("psugsug greedy red green", source(player));
        assertEquals("greedy=red green", player.nextMessage());
    }
}
