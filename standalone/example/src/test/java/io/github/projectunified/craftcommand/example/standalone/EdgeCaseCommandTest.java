package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.CommandInfo;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.projectunified.craftcommand.example.standalone.TestHelpers.assertSuggestionsContain;

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
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"greedyempty"}));
    }

    @Test
    public void testGreedyDefault() {
        assertTrue(cmd.execute("sender", new String[]{"greedydef"}));
        assertTrue(cmd.execute("sender", new String[]{"greedydef", "custom"}));
    }

    @Test
    public void testEnumParam() {
        assertTrue(cmd.execute("sender", new String[]{"enum", "RED"}));
        assertTrue(cmd.execute("sender", new String[]{"enum", "GREEN"}));
    }

    @Test
    public void testEnumParamInvalid() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"enum", "PURPLE"}));
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
        assertTrue(cmd.execute("sender", new String[]{"defname"}));
        assertTrue(cmd.execute("sender", new String[]{"defname", "world"}));
    }

    @Test
    public void testMultiAlias() {
        assertTrue(cmd.execute("sender", new String[]{"multi", "value1"}));
        assertTrue(cmd.execute("sender", new String[]{"m", "value2"}));
    }

    @Test
    public void testTabCompletion() {
        List<String> suggestions = cmd.tabComplete("sender", new String[]{""});
        assertSuggestionsContain(suggestions, "greedyempty", "greedydef", "enum", "desc", "defname", "multi");
    }

    @Test
    public void testGreedyDefaultTabComplete() {
        List<String> suggestions = cmd.tabComplete("sender", new String[]{"greedydef", ""});
        assertNotNull(suggestions);
    }
}
