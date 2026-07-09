package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.CommandInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.projectunified.craftcommand.example.standalone.TestHelpers.assertSuggestionsContain;
import static org.junit.jupiter.api.Assertions.*;

public class EdgeCaseCommandTest extends AbstractStandaloneCommandTest {
    private EdgeCaseCommand instance;

    @Override
    protected void registerCommand() {
        instance = new EdgeCaseCommand();
        manager.register(instance);
    }

    @Override
    protected String getCommandName() {
        return "edge";
    }

    @Test
    public void testGreedyEmptyArgs() {
        assertThrows(RuntimeException.class, () -> execute("greedyempty"));
    }

    @Test
    public void testGreedyDefault() {
        assertTrue(execute("greedydef"));
        assertEquals(List.of("greedydef='fallback'"), sender.getMessages());
        sender.getMessages().clear();
        assertTrue(execute("greedydef", "custom"));
        assertEquals(List.of("greedydef='custom'"), sender.getMessages());
    }

    @Test
    public void testEnumParam() {
        assertTrue(execute("enum", "RED"));
        assertEquals(List.of("enum=RED"), sender.getMessages());
        sender.getMessages().clear();
        assertTrue(execute("enum", "GREEN"));
        assertEquals(List.of("enum=GREEN"), sender.getMessages());
    }

    @Test
    public void testEnumParamInvalid() {
        assertThrows(RuntimeException.class, () -> execute("enum", "PURPLE"));
    }

    @Test
    public void testDescriptionInCommandInfo() {
        assertEquals("Edge case combinations", cmd.getDescription());
        List<CommandInfo> infoList = manager.getCommandInfo(instance);
        assertNotNull(infoList);
        assertFalse(infoList.isEmpty());
    }

    @Test
    public void testDefaultName() {
        assertTrue(execute("defname"));
        assertEquals(List.of("defname=hello"), sender.getMessages());
        sender.getMessages().clear();
        assertTrue(execute("defname", "world"));
        assertEquals(List.of("defname=world"), sender.getMessages());
    }

    @Test
    public void testMultiAlias() {
        assertTrue(execute("multi", "value1"));
        assertEquals(List.of("multi=value1"), sender.getMessages());
        sender.getMessages().clear();
        assertTrue(execute("m", "value2"));
        assertEquals(List.of("multi=value2"), sender.getMessages());
    }

    @Test
    public void testTabCompletion() {
        List<String> suggestions = tabComplete("");
        assertSuggestionsContain(suggestions, "greedyempty", "greedydef", "enum", "desc", "defname", "multi");
    }

    @Test
    public void testGreedyDefaultTabComplete() {
        List<String> suggestions = tabComplete("greedydef", "");
        assertNotNull(suggestions);
    }
}
