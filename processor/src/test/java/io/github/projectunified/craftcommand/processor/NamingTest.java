package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.ClassName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NamingTest {

    private static final ClassName COMMAND = ClassName.get("com.example", "MyCommand");

    @Test
    public void testSuggestMethod() {
        assertEquals("suggestMyCommandAdd_0", Naming.suggestMethod(COMMAND, "add", 0));
    }

    @Test
    public void testSuggestMethodKeepsCommandAndSubcommand() {
        String result = Naming.suggestMethod(COMMAND, "default", 0);
        assertTrue(result.contains("MyCommand"));
        assertTrue(result.contains("Default"));
    }

    @Test
    public void testSuggestMethodEscapesUnusableNames() {
        assertEquals("suggestMyCommandStringWithDefault_1", Naming.suggestMethod(COMMAND, "string-with-default", 1));
    }

    @Test
    public void testSubcommandField() {
        ClassName nested = COMMAND.nestedClass("SubCommands");
        assertEquals("subInstanceMyCommandSubCommands", Naming.subcommandField(nested));
    }

    @Test
    public void testExecuteAndSuggestHelpers() {
        ClassName nested = COMMAND.nestedClass("Panel").nestedClass("Commands");
        assertEquals("executeMyCommandPanelCommands", Naming.executeHelper(nested));
        assertEquals("suggestMyCommandPanelCommands", Naming.suggestHelper(nested));
    }
}
