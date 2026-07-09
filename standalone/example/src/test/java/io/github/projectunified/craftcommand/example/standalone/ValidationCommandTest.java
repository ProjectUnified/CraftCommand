package io.github.projectunified.craftcommand.example.standalone;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ValidationCommandTest extends AbstractStandaloneCommandTest {

    @Override
    protected void registerCommand() {
        manager.register(new ValidationCommand());
    }

    @Override
    protected String getCommandName() {
        return "validate";
    }

    @Test
    public void testMinOnly() {
        assertTrue(cmd.execute("sender", new String[]{"min", "5"}));
    }

    @Test
    public void testMinOnlyBelowMin() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"min", "-1"}));
    }

    @Test
    public void testMaxOnly() {
        assertTrue(cmd.execute("sender", new String[]{"max", "50"}));
    }

    @Test
    public void testMaxOnlyAboveMax() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"max", "101"}));
    }

    @Test
    public void testMinMax() {
        assertTrue(cmd.execute("sender", new String[]{"minmax", "50"}));
    }

    @Test
    public void testMinMaxBelowMin() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"minmax", "-1"}));
    }

    @Test
    public void testMinMaxAboveMax() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"minmax", "101"}));
    }

    @Test
    public void testMinCustomMessage() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"minmsg", "-5"}));
        assertTrue(ex.getMessage().contains("value"), "Message should mention param name: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("0"), "Message should mention min value: " + ex.getMessage());
    }

    @Test
    public void testMaxCustomMessage() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"maxmsg", "200"}));
        assertTrue(ex.getMessage().contains("value"), "Message should mention param name: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("100"), "Message should mention max value: " + ex.getMessage());
    }

    @Test
    public void testVwCustomMessage() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"vwmsg", "-1"}));
        assertTrue(ex.getMessage().contains("Must be non-negative"), "Message should contain custom text: " + ex.getMessage());
    }

    @Test
    public void testMinVwStack() {
        assertTrue(cmd.execute("sender", new String[]{"minvw", "5"}));
    }

    @Test
    public void testMinVwStackBelowMin() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"minvw", "-1"}));
    }

    @Test
    public void testDefaultMin() {
        assertTrue(cmd.execute("sender", new String[]{"defmin"}));
        assertTrue(cmd.execute("sender", new String[]{"defmin", "10"}));
    }

    @Test
    public void testDefaultMinBelowMin() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"defmin", "-1"}));
    }

    @Test
    public void testDefaultMax() {
        assertTrue(cmd.execute("sender", new String[]{"defmax"}));
        assertTrue(cmd.execute("sender", new String[]{"defmax", "75"}));
    }

    @Test
    public void testDefaultMaxAboveMax() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"defmax", "150"}));
    }

    @Test
    public void testDefaultVw() {
        assertTrue(cmd.execute("sender", new String[]{"defvw"}));
        assertTrue(cmd.execute("sender", new String[]{"defvw", "30"}));
    }

    @Test
    public void testDefaultVwOutOfRange() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"defvw", "60"}));
    }
}
