package io.github.projectunified.craftcommand.example.standalone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static io.github.projectunified.craftcommand.example.standalone.TestHelpers.assertSuggestionsContain;
import static org.junit.jupiter.api.Assertions.*;

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
        assertTrue(execute("5", "10"));
        assertEquals("Result: 15", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // Basic Arithmetic Subcommands
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testAdd() {
        assertTrue(execute("add", "5", "10"));
        assertEquals("Result: 15", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testSubtract() {
        assertTrue(execute("sub", "100", "50"));
        assertEquals("Result: 50", sender.getMessages().get(0));
        sender.getMessages().clear();
        assertTrue(execute("subtract", "100", "50"));
        assertEquals("Result: 50", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testMultiply() {
        assertTrue(execute("mul", "6", "7"));
        assertEquals("Result: 42", sender.getMessages().get(0));
        sender.getMessages().clear();
        assertTrue(execute("multiply", "6", "7"));
        assertEquals("Result: 42", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testDivide() {
        assertTrue(execute("div", "10", "2"));
        assertEquals("Result: 5.0", sender.getMessages().get(0));
        sender.getMessages().clear();
        assertTrue(execute("divide", "10", "2"));
        assertEquals("Result: 5.0", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testDivideByZero() {
        assertThrows(RuntimeException.class, () -> execute("div", "10", "0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // @Suggest Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testFieldSuggest() {
        List<String> suggestions = tabComplete("op", "");
        assertSuggestionsContain(suggestions, "add", "subtract", "multiply", "divide");
    }

    @Test
    public void testMethodSuggest() {
        List<String> suggestions = tabComplete("mode", "");
        assertSuggestionsContain(suggestions, "basic", "scientific", "programmer");
    }

    @Test
    public void testRunOp() {
        assertTrue(execute("op", "add", "5", "10"));
        assertEquals("Result: 15", sender.getMessages().get(0));
        sender.getMessages().clear();
        assertTrue(execute("op", "multiply", "3", "4"));
        assertEquals("Result: 12", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testRunOpInvalidOperator() {
        assertThrows(RuntimeException.class, () -> execute("op", "invalid", "5", "10"));
    }

    @Test
    public void testRunMode() {
        assertTrue(execute("mode", "scientific"));
        assertEquals("Mode set to: scientific", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // @Default Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testOptionalWithDefault() {
        assertTrue(execute("print", "Hello World"));
        assertEquals("Hello World", sender.getMessages().get(0));
        sender.getMessages().clear();
        assertTrue(execute("print", "Custom:", "Hello"));
        assertEquals("Custom: Hello", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testOptionalIntWithDefault() {
        assertTrue(execute("repeat", "test", "3"));
        assertEquals(List.of("test", "test", "test"), sender.getMessages());
        sender.getMessages().clear();
        assertTrue(execute("repeat", "test"));
        assertEquals(List.of("test"), sender.getMessages());
        sender.getMessages().clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // @Greedy Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testGreedyString() {
        assertTrue(execute("echo", "Hello", "World", "Foo"));
        assertEquals("Hello World Foo", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testGreedyNonString() {
        assertTrue(execute("parse", "3.14"));
        assertEquals("Parsed: 3.14", sender.getMessages().get(0));
        sender.getMessages().clear();
        assertTrue(execute("parse", "2.5e10"));
        assertEquals("Parsed: 2.5E10", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testGreedyArray() {
        assertTrue(execute("sum", "1", "2", "3"));
        assertEquals("Sum: 6", sender.getMessages().get(0));
        sender.getMessages().clear();
        assertTrue(execute("sum", "10"));
        assertEquals("Sum: 10", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testGreedyNonStringInvalid() {
        assertThrows(RuntimeException.class, () -> execute("parse", "not_a_number"));
    }

    // ═══════════════════════════════════════════════════════════════
    // @Name Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testNameOverride() {
        assertTrue(execute("msg", "Player", "Hello World"));
        assertEquals("To Player: Hello World", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // @Resolve Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testParameterResolve() {
        assertTrue(execute("point", "10.5", "20.5"));
        assertEquals("Point: (10.5, 20.5)", sender.getMessages().get(0));
        sender.getMessages().clear();
        assertTrue(execute("point", "10.5"));
        assertEquals("Point: (10.5, 0.0)", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testSenderResolve() {
        // whoami uses @Resolve("resolveSender") CustomSender — resolver wraps TestSender as delegate
        // CustomSender.sendMessage forwards to TestSender.sendMessage
        assertTrue(execute("whoami"));
        assertEquals("You are: test", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testSenderResolveDifferentValues() {
        // Different sender names produce different CustomSender instances
        TestSender alice = new TestSender("Alice");
        TestSender bob = new TestSender("Bob");
        assertTrue(cmd.execute(alice, new String[]{"whoami"}));
        assertEquals("You are: Alice", alice.getMessages().get(0));
        assertTrue(cmd.execute(bob, new String[]{"whoami"}));
        assertEquals("You are: Bob", bob.getMessages().get(0));
    }

    // ═══════════════════════════════════════════════════════════════
    // @ValidateWith Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testValidateCoordinate() {
        assertTrue(execute("coord", "100", "200", "300"));
        assertEquals("Location set to: (100.0, 200.0, 300.0)", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testValidateCoordinateOutOfBounds() {
        assertThrows(RuntimeException.class, () -> execute("coord", "2000", "200", "300"));
    }

    // ═══════════════════════════════════════════════════════════════
    // @Min/@Max Feature
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testLevelValid() {
        assertTrue(execute("level", "50", "5.0"));
        assertEquals("Level 50 with multiplier 5.0", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testLevelBelowMin() {
        assertThrows(RuntimeException.class, () -> execute("level", "0", "5.0"));
    }

    @Test
    public void testLevelAboveMax() {
        assertThrows(RuntimeException.class, () -> execute("level", "101", "5.0"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Enum Parameter
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testEnumOp() {
        assertTrue(execute("enumop", "ADD", "5", "10"));
        assertEquals("Result: 15", sender.getMessages().get(0));
        sender.getMessages().clear();
        assertTrue(execute("enumop", "MULTIPLY", "6", "7"));
        assertEquals("Result: 42", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testEnumOpInvalid() {
        assertThrows(RuntimeException.class, () -> execute("enumop", "INVALID", "5", "10"));
    }

    // ═══════════════════════════════════════════════════════════════
    // Nested Subcommand Class
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testAdvancedDefault() {
        assertTrue(execute("advanced"));
        assertEquals("Advanced operations: power, sqrt", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testAdvancedPower() {
        assertTrue(execute("advanced", "power", "2", "10"));
        assertEquals("Result: " + Math.pow(2, 10), sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testAdvancedSqrt() {
        assertTrue(execute("advanced", "sqrt", "16"));
        assertEquals("Result: 4.0", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testAdvancedSqrtNegative() {
        assertThrows(RuntimeException.class, () -> execute("advanced", "sqrt", "-1"));
    }

    @Test
    public void testAdvancedLog() {
        assertTrue(execute("advanced", "log", "100", "10"));
        assertEquals("Result: 2.0", sender.getMessages().get(0));
        sender.getMessages().clear();
        assertTrue(execute("advanced", "log", "100"));
        assertEquals("Result: " + (Math.log(100) / Math.log(10)), sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // Inner Class @Resolve Outer Method (Issue #4)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testInnerClassResolveOuterDefault() {
        assertTrue(execute("resolve", "10", "20"));
        assertEquals("Resolved point: (10.0, 20.0)", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testInnerClassResolveOuterWithOptional() {
        assertTrue(execute("resolve", "5"));
        assertEquals("Resolved point: (5.0, 0.0)", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testInnerClassResolveOuterSubcommand() {
        assertTrue(execute("resolve", "display", "3", "4"));
        assertEquals("Displaying point: (3.0, 4.0)", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    @Test
    public void testInnerClassResolveOuterSubcommandWithOptional() {
        assertTrue(execute("resolve", "display", "7"));
        assertEquals("Displaying point: (7.0, 0.0)", sender.getMessages().get(0));
        sender.getMessages().clear();
    }

    // ═══════════════════════════════════════════════════════════════
    // Tab Completion
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testTabCompletionSubcommands() {
        List<String> suggestions = tabComplete("");
        assertSuggestionsContain(suggestions, "add", "sub", "mul", "div", "op", "mode", "print",
                "repeat", "echo", "sum", "msg", "point", "whoami", "coord", "level", "enumop",
                "advanced", "resolve");
    }

    @Test
    public void testTabCompletionAdvanced() {
        List<String> suggestions = tabComplete("advanced", "");
        assertSuggestionsContain(suggestions, "power", "sqrt", "log");
    }

    @Test
    public void testTabCompletionOp() {
        List<String> suggestions = tabComplete("op", "");
        assertSuggestionsContain(suggestions, "add", "subtract", "multiply", "divide");
    }

    @Test
    public void testTabCompletionMode() {
        List<String> suggestions = tabComplete("mode", "");
        assertSuggestionsContain(suggestions, "basic", "scientific", "programmer");
    }

    // ═══════════════════════════════════════════════════════════════
    // Error Handling
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testMissingArguments() {
        assertThrows(RuntimeException.class, () -> execute("add", "5"));
    }

    @Test
    public void testInvalidNumberFormat() {
        assertThrows(RuntimeException.class, () -> execute("add", "abc", "10"));
    }

    @Test
    public void testUnknownSubcommand() {
        assertThrows(RuntimeException.class, () -> execute("nonexistent", "5", "10"));
    }
}
