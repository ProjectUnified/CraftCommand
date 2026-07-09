package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.ClassName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class NamingTest {

    @Test
    public void testClassPath() {
        ClassName className = ClassName.get("com.example", "MyCommand");
        String path = Naming.classPath(className);
        assertEquals("mycommand", path);
    }

    @Test
    public void testSuggestMethod() {
        String result = Naming.suggestMethod("com.example.MyCommand", "add", 0);
        assertNotNull(result);
        assertTrue(result.contains("MyCommand"));
        assertTrue(result.contains("add"));
    }

    @Test
    public void testSuggestMethodDefault() {
        String result = Naming.suggestMethod("com.example.MyCommand", "default", 0);
        assertNotNull(result);
        assertTrue(result.contains("MyCommand"));
    }

    @Test
    public void testSubcommandField() {
        ClassName className = ClassName.get("com.example", "SubCommands");
        String field = Naming.subcommandField(className);
        assertNotNull(field);
        assertTrue(field.contains("subcommand"));
    }
}
