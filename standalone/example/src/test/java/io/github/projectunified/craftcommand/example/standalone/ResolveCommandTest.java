package io.github.projectunified.craftcommand.example.standalone;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class ResolveCommandTest extends AbstractStandaloneCommandTest {

    @Override
    protected void registerCommand() {
        manager.register(new ResolveCommand());
    }

    @Override
    protected String getCommandName() {
        return "resolv";
    }

    @Test
    public void testNamedResolve() {
        assertTrue(execute("named", "10", "20"));
        assertEquals(List.of("named=10.0,20.0"), sender.getMessages());
    }

    @Test
    public void testSenderResolve() {
        execute("sender");
    }

    @Test
    public void testSenderResolveDifferentValues() {
        cmd.execute(new TestSender("Alice"), new String[]{"sender"});
        cmd.execute(new TestSender("Bob"), new String[]{"sender"});
    }

    @Test
    public void testResolveDefault() {
        assertTrue(execute("def", "10"));
        assertEquals(List.of("def=10.0,0.0"), sender.getMessages());
        sender.getMessages().clear();
        assertTrue(execute("def", "10", "20"));
        assertEquals(List.of("def=10.0,20.0"), sender.getMessages());
    }

    @Test
    public void testResolveMin() {
        assertTrue(execute("min", "5", "10"));
        assertEquals(List.of("min=5.0,10.0"), sender.getMessages());
    }

    @Test
    public void testResolveMinNegative() {
        assertTrue(execute("min", "-1", "10"));
        assertEquals(List.of("min=-1.0,10.0"), sender.getMessages());
    }

    @Test
    public void testResolveVw() {
        assertTrue(execute("vw", "10", "20"));
        assertEquals(List.of("vw=10.0,20.0"), sender.getMessages());
    }

    @Test
    public void testResolveVwNegative() {
        assertThrows(RuntimeException.class, () -> execute("vw", "-1", "10"));
    }

    @Test
    public void testImplicitResolve() {
        assertTrue(execute("implicit", "3", "4"));
        assertEquals(List.of("implicit=3.0,4.0"), sender.getMessages());
    }

    @Test
    public void testImplicitResolveWithNegative() {
        assertTrue(execute("implicit", "-1", "-2"));
        assertEquals(List.of("implicit=-1.0,-2.0"), sender.getMessages());
    }
}
