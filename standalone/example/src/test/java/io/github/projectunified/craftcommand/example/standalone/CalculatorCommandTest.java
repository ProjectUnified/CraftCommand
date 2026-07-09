package io.github.projectunified.craftcommand.example.standalone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static io.github.projectunified.craftcommand.example.standalone.TestHelpers.assertSuggestionsContain;

public class CalculatorCommandTest extends AbstractStandaloneCommandTest {
    private CalculatorCommand instance;

    @Override
    protected void registerCommand() {
        instance = new CalculatorCommand();
        manager.register(instance);
    }

    @Override
    protected String getCommandName() {
        return "calc";
    }

    // ═══════════════════════════════════════════════════════════════
    // Command Metadata
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testCommandMetadata() {
        assertNotNull(cmd);
        assertEquals("calc", cmd.getName());
        assertTrue(cmd.getAliases().contains("c"));
    }

    @Test
    public void testCommandInfo() {
        List<io.github.projectunified.craftcommand.CommandInfo> infoList = manager.getCommandInfo(instance);
        assertNotNull(infoList);
        assertFalse(infoList.isEmpty());

        io.github.projectunified.craftcommand.CommandInfo defaultInfo = infoList.stream()
                .filter(info -> info.getPath().size() == 1 && info.getPath().get(0).equals("calc"))
                .findFirst()
                .orElse(null);
        assertNotNull(defaultInfo);
        assertEquals("<num1> <num2>", defaultInfo.getUsage());
    }

    // ═══════════════════════════════════════════════════════════════
    // @Default Action
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testDefaultAction() {
        assertTrue(cmd.execute("sender", new String[]{"5", "10"}));
    }

    // ═══════════════════════════════════════════════════════════════
    // Basic Arithmetic Subcommands
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testAdd() {
        assertTrue(cmd.execute("sender", new String[]{"add", "5", "10"}));
    }

    @Test
    public void testSubtract() {
        assertTrue(cmd.execute("sender", new String[]{"sub", "100", "50"}));
        assertTrue(cmd.execute("sender", new String[]{"subtract", "100", "50"}));
    }

    @Test
    public void testMultiply() {
        assertTrue(cmd.execute("sender", new String[]{"mul", "6", "7"}));
        assertTrue(cmd.execute("sender", new String[]{"multiply", "6", "7"}));
    }

    @Test
    public void testDivide() {
        assertTrue(cmd.execute("sender", new String[]{"div", "10", "2"}));
        assertTrue(cmd.execute("sender", new String[]{"divide", "10", "2"}));
    }

    @Test
    public void testDivideByZero() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"div", "10", "0"}));
    }

    // ═══════════════════════════════════════════════════════════════
    // @Suggest Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testFieldSuggest() {
        List<String> suggestions = cmd.tabComplete("sender", new String[]{"op", ""});
        assertSuggestionsContain(suggestions, "add", "subtract", "multiply", "divide");
    }

    @Test
    public void testMethodSuggest() {
        List<String> suggestions = cmd.tabComplete("sender", new String[]{"mode", ""});
        assertSuggestionsContain(suggestions, "basic", "scientific", "programmer");
    }

    @Test
    public void testRunOp() {
        assertTrue(cmd.execute("sender", new String[]{"op", "add", "5", "10"}));
        assertTrue(cmd.execute("sender", new String[]{"op", "multiply", "3", "4"}));
    }

    @Test
    public void testRunOpInvalidOperator() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"op", "invalid", "5", "10"}));
    }

    @Test
    public void testRunMode() {
        assertTrue(cmd.execute("sender", new String[]{"mode", "scientific"}));
    }

    // ═══════════════════════════════════════════════════════════════
    // @Default Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testOptionalWithDefault() {
        assertTrue(cmd.execute("sender", new String[]{"print", "Custom:", "Hello"}));
        assertTrue(cmd.execute("sender", new String[]{"print", "Hello World"}));
    }

    @Test
    public void testOptionalIntWithDefault() {
        assertTrue(cmd.execute("sender", new String[]{"repeat", "test", "3"}));
        assertTrue(cmd.execute("sender", new String[]{"repeat", "test"}));
    }

    // ═══════════════════════════════════════════════════════════════
    // @Greedy Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testGreedyString() {
        assertTrue(cmd.execute("sender", new String[]{"echo", "Hello", "World", "Foo"}));
    }

    @Test
    public void testGreedyNonString() {
        assertTrue(cmd.execute("sender", new String[]{"parse", "3.14"}));
        assertTrue(cmd.execute("sender", new String[]{"parse", "2.5e10"}));
    }

    @Test
    public void testGreedyArray() {
        assertTrue(cmd.execute("sender", new String[]{"sum", "1", "2", "3"}));
        assertTrue(cmd.execute("sender", new String[]{"sum", "10"}));
    }

    @Test
    public void testGreedyNonStringInvalid() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"parse", "not_a_number"}));
    }

    // ═══════════════════════════════════════════════════════════════
    // @Name Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testNameOverride() {
        assertTrue(cmd.execute("sender", new String[]{"msg", "Player", "Hello World"}));
    }

    // ═══════════════════════════════════════════════════════════════
    // @Resolve Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testParameterResolve() {
        assertTrue(cmd.execute("sender", new String[]{"point", "10.5", "20.5"}));
        assertTrue(cmd.execute("sender", new String[]{"point", "10.5"}));
    }

    @Test
    public void testSenderResolve() {
        assertTrue(cmd.execute("mySender", new String[]{"whoami"}));
    }

    @Test
    public void testSenderResolveDifferentValues() {
        assertTrue(cmd.execute("Alice", new String[]{"whoami"}));
        assertTrue(cmd.execute("Bob", new String[]{"whoami"}));
    }

    // ═══════════════════════════════════════════════════════════════
    // @ValidateWith Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testValidateCoordinate() {
        assertTrue(cmd.execute("sender", new String[]{"coord", "100", "200", "300"}));
    }

    @Test
    public void testValidateCoordinateOutOfBounds() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"coord", "2000", "200", "300"}));
    }

    // ═══════════════════════════════════════════════════════════════
    // @Min/@Max Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testLevelValid() {
        assertTrue(cmd.execute("sender", new String[]{"level", "50", "5.0"}));
    }

    @Test
    public void testLevelBelowMin() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"level", "0", "5.0"}));
    }

    @Test
    public void testLevelAboveMax() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"level", "101", "5.0"}));
    }

    // ═══════════════════════════════════════════════════════════════
    // Enum Parameter
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testEnumOp() {
        assertTrue(cmd.execute("sender", new String[]{"enumop", "ADD", "5", "10"}));
        assertTrue(cmd.execute("sender", new String[]{"enumop", "MULTIPLY", "6", "7"}));
    }

    @Test
    public void testEnumOpInvalid() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"enumop", "INVALID", "5", "10"}));
    }

    // ═══════════════════════════════════════════════════════════════
    // Nested Subcommand Class
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testAdvancedDefault() {
        assertTrue(cmd.execute("sender", new String[]{"advanced"}));
    }

    @Test
    public void testAdvancedPower() {
        assertTrue(cmd.execute("sender", new String[]{"advanced", "power", "2", "10"}));
    }

    @Test
    public void testAdvancedSqrt() {
        assertTrue(cmd.execute("sender", new String[]{"advanced", "sqrt", "16"}));
    }

    @Test
    public void testAdvancedSqrtNegative() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"advanced", "sqrt", "-1"}));
    }

    @Test
    public void testAdvancedLog() {
        assertTrue(cmd.execute("sender", new String[]{"advanced", "log", "100", "10"}));
        assertTrue(cmd.execute("sender", new String[]{"advanced", "log", "100"}));
    }

    // ═══════════════════════════════════════════════════════════════
    // Inner Class @Resolve Outer Method (Issue #4)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testInnerClassResolveOuterDefault() {
        assertTrue(cmd.execute("sender", new String[]{"resolve", "10", "20"}));
    }

    @Test
    public void testInnerClassResolveOuterWithOptional() {
        assertTrue(cmd.execute("sender", new String[]{"resolve", "5"}));
    }

    @Test
    public void testInnerClassResolveOuterSubcommand() {
        assertTrue(cmd.execute("sender", new String[]{"resolve", "display", "3", "4"}));
    }

    @Test
    public void testInnerClassResolveOuterSubcommandWithOptional() {
        assertTrue(cmd.execute("sender", new String[]{"resolve", "display", "7"}));
    }

    // ═══════════════════════════════════════════════════════════════
    // Tab Completion
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testTabCompletionSubcommands() {
        List<String> suggestions = cmd.tabComplete("sender", new String[]{""});
        assertSuggestionsContain(suggestions, "add", "sub", "mul", "div", "op", "mode", "print",
                "repeat", "echo", "sum", "msg", "point", "whoami", "coord", "level", "enumop",
                "advanced", "resolve");
    }

    @Test
    public void testTabCompletionAdvanced() {
        List<String> suggestions = cmd.tabComplete("sender", new String[]{"advanced", ""});
        assertSuggestionsContain(suggestions, "power", "sqrt", "log");
    }

    @Test
    public void testTabCompletionOp() {
        List<String> suggestions = cmd.tabComplete("sender", new String[]{"op", ""});
        assertSuggestionsContain(suggestions, "add", "subtract", "multiply", "divide");
    }

    @Test
    public void testTabCompletionMode() {
        List<String> suggestions = cmd.tabComplete("sender", new String[]{"mode", ""});
        assertSuggestionsContain(suggestions, "basic", "scientific", "programmer");
    }

    // ═══════════════════════════════════════════════════════════════
    // Error Handling
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testMissingArguments() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"add", "5"}));
    }

    @Test
    public void testInvalidNumberFormat() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"add", "abc", "10"}));
    }

    @Test
    public void testUnknownSubcommand() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"nonexistent", "5", "10"}));
    }
}
