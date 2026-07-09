package io.github.projectunified.craftcommand.example.standalone;

import org.junit.jupiter.api.Test;

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
        assertTrue(cmd.execute("sender", new String[]{"named", "10", "20"}));
    }

    @Test
    public void testSenderResolve() {
        assertTrue(cmd.execute("mySender", new String[]{"sender"}));
    }

    @Test
    public void testSenderResolveDifferentValues() {
        assertTrue(cmd.execute("Alice", new String[]{"sender"}));
        assertTrue(cmd.execute("Bob", new String[]{"sender"}));
    }

    @Test
    public void testResolveDefault() {
        assertTrue(cmd.execute("sender", new String[]{"def", "10"}));
        assertTrue(cmd.execute("sender", new String[]{"def", "10", "20"}));
    }

    @Test
    public void testResolveMin() {
        assertTrue(cmd.execute("sender", new String[]{"min", "5", "10"}));
    }

    @Test
    public void testResolveMinNegative() {
        assertTrue(cmd.execute("sender", new String[]{"min", "-1", "10"}));
    }

    @Test
    public void testResolveVw() {
        assertTrue(cmd.execute("sender", new String[]{"vw", "10", "20"}));
    }

    @Test
    public void testResolveVwNegative() {
        assertThrows(RuntimeException.class, () -> cmd.execute("sender", new String[]{"vw", "-1", "10"}));
    }

    @Test
    public void testImplicitResolve() {
        assertTrue(cmd.execute("sender", new String[]{"implicit", "3", "4"}));
    }

    @Test
    public void testImplicitResolveWithNegative() {
        assertTrue(cmd.execute("sender", new String[]{"implicit", "-1", "-2"}));
    }
}
