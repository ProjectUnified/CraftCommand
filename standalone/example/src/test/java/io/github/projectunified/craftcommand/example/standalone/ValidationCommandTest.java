package io.github.projectunified.craftcommand.example.standalone;

import org.junit.jupiter.api.Test;

import java.util.List;

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
        assertTrue(execute("min", "5"));
        assertEquals(List.of("min=5"), sender.getMessages());
    }

    @Test
    public void testMinOnlyBelowMin() {
        assertThrows(RuntimeException.class, () -> execute("min", "-1"));
    }

    @Test
    public void testMaxOnly() {
        assertTrue(execute("max", "50"));
        assertEquals(List.of("max=50"), sender.getMessages());
    }

    @Test
    public void testMaxOnlyAboveMax() {
        assertThrows(RuntimeException.class, () -> execute("max", "101"));
    }

    @Test
    public void testMinMax() {
        assertTrue(execute("minmax", "50"));
        assertEquals(List.of("minmax=50"), sender.getMessages());
    }

    @Test
    public void testMinMaxBelowMin() {
        assertThrows(RuntimeException.class, () -> execute("minmax", "-1"));
    }

    @Test
    public void testMinMaxAboveMax() {
        assertThrows(RuntimeException.class, () -> execute("minmax", "101"));
    }

    @Test
    public void testMinCustomMessage() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> execute("minmsg", "-5"));
        assertTrue(ex.getMessage().contains("value"), "Message should mention param name: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("0"), "Message should mention min value: " + ex.getMessage());
    }

    @Test
    public void testMaxCustomMessage() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> execute("maxmsg", "200"));
        assertTrue(ex.getMessage().contains("value"), "Message should mention param name: " + ex.getMessage());
        assertTrue(ex.getMessage().contains("100"), "Message should mention max value: " + ex.getMessage());
    }

    @Test
    public void testVwCustomMessage() {
        RuntimeException ex = assertThrows(RuntimeException.class, () -> execute("vwmsg", "-1"));
        assertTrue(ex.getMessage().contains("Must be non-negative"), "Message should contain custom text: " + ex.getMessage());
    }

    @Test
    public void testMinVwStack() {
        assertTrue(execute("minvw", "5"));
        assertEquals(List.of("minvw=5"), sender.getMessages());
    }

    @Test
    public void testMinVwStackBelowMin() {
        assertThrows(RuntimeException.class, () -> execute("minvw", "-1"));
    }

    @Test
    public void testDefaultMin() {
        assertTrue(execute("defmin"));
        assertEquals(List.of("defmin=50"), sender.getMessages());
        sender.getMessages().clear();
        assertTrue(execute("defmin", "10"));
        assertEquals(List.of("defmin=10"), sender.getMessages());
    }

    @Test
    public void testDefaultMinBelowMin() {
        assertThrows(RuntimeException.class, () -> execute("defmin", "-1"));
    }

    @Test
    public void testDefaultMax() {
        assertTrue(execute("defmax"));
        assertEquals(List.of("defmax=50"), sender.getMessages());
        sender.getMessages().clear();
        assertTrue(execute("defmax", "75"));
        assertEquals(List.of("defmax=75"), sender.getMessages());
    }

    @Test
    public void testDefaultMaxAboveMax() {
        assertThrows(RuntimeException.class, () -> execute("defmax", "150"));
    }

    @Test
    public void testDefaultVw() {
        assertTrue(execute("defvw"));
        assertEquals(List.of("defvw=25"), sender.getMessages());
        sender.getMessages().clear();
        assertTrue(execute("defvw", "30"));
        assertEquals(List.of("defvw=30"), sender.getMessages());
    }

    @Test
    public void testDefaultVwOutOfRange() {
        assertThrows(RuntimeException.class, () -> execute("defvw", "60"));
    }
}
