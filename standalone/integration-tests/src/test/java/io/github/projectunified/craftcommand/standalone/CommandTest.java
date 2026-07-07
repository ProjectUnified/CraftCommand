package io.github.projectunified.craftcommand.standalone;

import io.github.projectunified.craftcommand.ArgumentResolver;
import io.github.projectunified.craftcommand.exception.CommandException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class CommandTest {
    private StandaloneCommandManager manager;
    private TestCommand commandInstance;
    private final java.util.Map<String, String> customMessages = new java.util.HashMap<>();

    @BeforeEach
    public void setUp() {
        customMessages.clear();
        manager = new StandaloneCommandManager() {
            @Override
            public String formatMessage(String key, String defaultValue, Object... args) {
                String template = customMessages.get(key);
                if (template != null) {
                    try {
                        return String.format(template, args);
                    } catch (Exception e) {
                        return template;
                    }
                }
                return super.formatMessage(key, defaultValue, args);
            }
        };

        // Register hierarchy resolver (CustomInterface -> CustomImpl)
        manager.registerResolver(TestCommand.CustomInterface.class, (sender, args, current) -> new TestCommand.CustomImpl(current));

        // Register custom resolver for CustomImpl to support converting sender
        manager.registerResolver(TestCommand.CustomImpl.class, (sender, args, current) -> {
            if (current == null) {
                return new TestCommand.CustomImpl(sender.toString() + "_converted");
            }
            return new TestCommand.CustomImpl(current);
        });

        // Register dynamic provider for enums
        manager.registerProvider(type -> {
            if (type.isEnum()) {
                return (sender, args, current) -> Enum.valueOf((Class<Enum>) type, current.toUpperCase());
            }
            return null;
        });

        // Register Point resolver
        manager.registerResolver(Point.class, new ArgumentResolver<Object, Point>() {
            @Override
            public Point resolve(Object sender, String[] args, String current) {
                if (args.length < 2) {
                    throw new IllegalArgumentException("Point requires 2 arguments: <x> <y>");
                }
                try {
                    double x = Double.parseDouble(args[0]);
                    double y = Double.parseDouble(args[1]);
                    return new Point(x, y);
                } catch (NumberFormatException e) {
                    throw new IllegalArgumentException("Invalid Point coordinate format");
                }
            }

            @Override
            public int getWidth() {
                return 2;
            }
        });

        commandInstance = new TestCommand();
        manager.register(commandInstance);
    }

    @Test
    public void testCommandMetadata() {
        StandaloneCommand cmd = manager.getCommand("test");
        assertNotNull(cmd);
        assertEquals("test", cmd.getName());
        assertTrue(cmd.getAliases().contains("t"));
        assertEquals("Test Command", cmd.getDescription());
    }

    @Test
    public void testCommandExecution() {
        StandaloneCommand cmd = manager.getCommand("test");

        // execute default: /test sword 5
        assertTrue(cmd.execute("sender", new String[]{"sword", "5"}));

        // execute default optional: /test sword
        assertTrue(cmd.execute("sender", new String[]{"sword"}));

        // execute subcommand: /test mode easy
        assertTrue(cmd.execute("sender", new String[]{"mode", "easy"}));

        // execute nested inner class subcommand default: /test nested hello
        assertTrue(cmd.execute("sender", new String[]{"nested", "hello"}));

        // execute nested inner class subcommand method: /test nested sub 42
        assertTrue(cmd.execute("sender", new String[]{"nested", "sub", "42"}));
    }

    @Test
    public void testTabCompletion() {
        StandaloneCommand cmd = manager.getCommand("test");

        // suggest subcommands and default first argument
        List<String> suggestions = cmd.tabComplete("sender", new String[]{""});
        assertTrue(suggestions.contains("mode"));
        assertTrue(suggestions.contains("greedy"));
        assertTrue(suggestions.contains("nested"));
        assertTrue(suggestions.contains("sword"));

        // suggest modes
        List<String> modeSuggestions = cmd.tabComplete("sender", new String[]{"mode", ""});
        assertTrue(modeSuggestions.contains("easy"));
        assertTrue(modeSuggestions.contains("hard"));

        // suggest nested inner class subcommands
        List<String> nestedSuggestions = cmd.tabComplete("sender", new String[]{"nested", ""});
        assertTrue(nestedSuggestions.contains("sub"));
        assertTrue(nestedSuggestions.contains("deep"));
    }

    @Test
    public void testDynamicResolution() {
        StandaloneCommand cmd = manager.getCommand("test");

        // execute dynamic enum: /test enum first
        assertTrue(cmd.execute("sender", new String[]{"enum", "first"}));

        // execute interface hierarchy: /test custom hello
        assertTrue(cmd.execute("sender", new String[]{"custom", "hello"}));
    }

    @Test
    public void testLocalResolvers() {
        StandaloneCommand cmd = manager.getCommand("test");

        // /test custom hello
        assertTrue(cmd.execute("sender", new String[]{"custom", "hello"}));

        // /test nested-inherit hello
        assertTrue(cmd.execute("sender", new String[]{"nested-inherit", "hello"}));

        // /test nested-override hello
        assertTrue(cmd.execute("sender", new String[]{"nested-override", "hello"}));
    }

    @Test
    public void testParameterLevelResolver() {
        StandaloneCommand cmd = manager.getCommand("test");

        // /test param-resolve hello -> internally validates custom.getName() == "resolved_hello"
        assertTrue(cmd.execute("sender", new String[]{"param-resolve", "hello"}));
    }

    @Test
    public void testFirstParameterSenderResolver() {
        StandaloneCommand cmd = manager.getCommand("test");

        // /test sender-resolve -> internally validates resolved sender.getName() == "mySender_resolved"
        assertTrue(cmd.execute("mySender", new String[]{"sender-resolve"}));
    }

    @Test
    public void testDeepSubcommandNesting() {
        StandaloneCommand cmd = manager.getCommand("test");

        // execute /test nested deep
        assertTrue(cmd.execute("sender", new String[]{"nested", "deep"}));

        // execute /test nested deep value 123
        assertTrue(cmd.execute("sender", new String[]{"nested", "deep", "value", "123"}));
    }

    @Test
    public void testParameterValidation() {
        StandaloneCommand cmd = manager.getCommand("test");

        // Valid execution: 7 is within [5, 10], length of "hello" is 5
        assertTrue(cmd.execute("sender", new String[]{"validate", "7", "hello"}));

        // Invalid: less than @Min(5)
        try {
            cmd.execute("sender", new String[]{"validate", "4", "hello"});
            fail("Expected RuntimeException from @Min");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("cannot be less than 5"));
        }

        // Invalid: greater than @Max(10)
        try {
            cmd.execute("sender", new String[]{"validate", "11", "hello"});
            fail("Expected RuntimeException from @Max");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("cannot be greater than 10"));
        }

        // Invalid: fails @ValidateWith custom validation
        try {
            cmd.execute("sender", new String[]{"validate", "7", "helloooo"});
            fail("Expected RuntimeException from @ValidateWith");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Input is too long"));
        }
    }

    @Test
    public void testMultiArgumentResolvers() {
        StandaloneCommand cmd = manager.getCommand("test");

        // 1. Valid execution with global multi-argument resolver (width = 2)
        assertTrue(cmd.execute("sender", new String[]{"point", "10.5", "20.5", "hello"}));

        // 2. Edge case: missing arguments for global resolver
        try {
            cmd.execute("sender", new String[]{"point", "10.5"});
            fail("Expected RuntimeException for missing arguments");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Usage") || e.getMessage().contains("Missing arguments") || e.getMessage().contains("Point requires 2 arguments"));
        }

        // 3. Edge case: invalid coordinates format
        try {
            cmd.execute("sender", new String[]{"point", "10abc", "20.5", "hello"});
            fail("Expected RuntimeException for invalid format");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Invalid Point coordinate format"));
        }

        // 4. Edge case: optional resolver parameters in local resolver method
        // With optional argument provided:
        assertTrue(cmd.execute("sender", new String[]{"point-local", "10.5", "15.5"}));
        // With optional argument omitted (falls back to default "0"):
        assertTrue(cmd.execute("sender", new String[]{"point-local", "10.5"}));

        // Missing required argument for local resolver:
        try {
            cmd.execute("sender", new String[]{"point-local"});
            fail("Expected RuntimeException for missing required argument in local resolver");
        } catch (RuntimeException e) {
            assertTrue(e.getMessage().contains("Missing arguments") || e.getMessage().contains("Usage"));
        }

        // 5. Edge case: tab completion index routing with width > 1
        // pt is width = 2.
        // Index 1 (first coordinate):
        List<String> sug1 = cmd.tabComplete("sender", new String[]{"point", ""});
        assertNotNull(sug1); // Should route to pt suggestions (empty)

        // Index 2 (second coordinate):
        List<String> sug2 = cmd.tabComplete("sender", new String[]{"point", "10.5", ""});
        assertNotNull(sug2); // Should route to pt suggestions (empty)

        // Index 3 (msg parameter after pt):
        List<String> sug3 = cmd.tabComplete("sender", new String[]{"point", "10.5", "20.5", ""});
        assertNotNull(sug3); // Should route to msg suggestions (empty)
    }

    @Test
    public void testCustomErrorMessagesAndExceptions() {
        StandaloneCommand cmd = manager.getCommand("test");

        customMessages.put("usage", "Invalid syntax. Correct format: %s");
        customMessages.put("error.range.max", "Value is too big: limit is %2$s");

        // 2. Verify bulk-set usage message and custom exception type
        try {
            cmd.execute("sender", new String[]{"mode"}); // Missing mode argument
            fail("Expected CommandException for usage");
        } catch (CommandException e) {
            assertTrue(e.getMessage().contains("Invalid syntax. Correct format:"));
        }

        // 3. Verify validation annotation with literal custom message
        try {
            cmd.execute("sender", new String[]{"validate-msg", "4", "8"});
            fail("Expected CommandException");
        } catch (CommandException e) {
            assertEquals("Value is too small!", e.getMessage());
        }

        // 4. Verify validation annotation using translation key from dictionary
        try {
            cmd.execute("sender", new String[]{"validate-msg", "6", "11"});
            fail("Expected CommandException");
        } catch (CommandException e) {
            assertEquals("Value is too big: limit is 10.0", e.getMessage());
        }

        // 5. Verify invalid sender
        try {
            cmd.execute("notASender", new String[]{"custom-sender"});
            fail("Expected CommandException");
        } catch (CommandException e) {
            assertTrue(e.getMessage().contains("Only"));
        }

        // 6. Verify missing argument
        try {
            cmd.execute("sender", new String[]{"point", "10.5", "20.5"});
            fail("Expected CommandException");
        } catch (CommandException e) {
            assertTrue(e.getMessage().contains("Missing arguments for parameter: msg"));
        }
    }

    @Test
    public void testPrimitiveTypes() {
        StandaloneCommand cmd = manager.getCommand("test");
        // /test primitives <long> <short> <byte> <char>
        assertTrue(cmd.execute("sender", new String[]{"primitives", "100", "5", "2", "A"}));

        // Invalid long format
        assertThrows(RuntimeException.class, () -> {
            cmd.execute("sender", new String[]{"primitives", "invalid_long", "5", "2", "A"});
        });

        // Invalid character format (length != 1)
        assertThrows(RuntimeException.class, () -> {
            cmd.execute("sender", new String[]{"primitives", "100", "5", "2", "AB"});
        });
    }
}
