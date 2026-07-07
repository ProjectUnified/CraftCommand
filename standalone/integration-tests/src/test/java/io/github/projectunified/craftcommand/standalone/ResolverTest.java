package io.github.projectunified.craftcommand.standalone;

import io.github.projectunified.craftcommand.ArgumentResolver;
import io.github.projectunified.craftcommand.ArgumentResolverProvider;
import io.github.projectunified.craftcommand.CommandManager;
import io.github.projectunified.craftcommand.exception.CommandException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Focused tests for {@link CommandManager#resolveParameter(Object, Class, String[], int[], String, boolean, String)}.
 *
 * <p>Covers the behavior central to the generated {@code resolve_<Type>} helpers:
 * width tracking via the shared index holder, optional/default handling, missing-argument
 * reporting, native-sender passthrough, and resolver lookup fallbacks (exact, hierarchy, provider).
 */
public class ResolverTest {

    private static final Object SENDER = "test-sender";

    private StandaloneCommandManager newManager() {
        return new StandaloneCommandManager();
    }

    @Test
    public void testSingleWidthRequiredResolvesAndAdvancesByOne() throws Exception {
        StandaloneCommandManager manager = newManager();
        boolean[] called = {false};
        manager.registerResolver(String.class, (sender, args, current) -> {
            called[0] = true;
            assertSame(SENDER, sender, "Native sender must be passed through unchanged");
            assertEquals("a", current);
            return "resolved:" + current;
        });

        String[] args = {"a", "b", "c"};
        int[] indexHolder = {0};

        String result = manager.resolveParameter(SENDER, String.class, args, indexHolder, "name", false, null);

        assertEquals("resolved:a", result);
        assertTrue(called[0], "Resolver must be invoked for a non-null single argument");
        assertEquals(1, indexHolder[0], "indexHolder must advance by width=1");
    }

    @Test
    public void testSingleWidthNullArgReturnsNullWithoutCallingResolver() throws Exception {
        StandaloneCommandManager manager = newManager();
        boolean[] called = {false};
        manager.registerResolver(String.class, (sender, args, current) -> {
            called[0] = true;
            return "x";
        });

        String[] args = {null};
        int[] indexHolder = {0};

        String result = manager.resolveParameter(SENDER, String.class, args, indexHolder, "name", false, null);

        assertNull(result, "Null argument must short-circuit to null without invoking the resolver");
        assertFalse(called[0], "Resolver must not be called when the argument is null");
        assertEquals(1, indexHolder[0], "indexHolder must still advance past the (null) argument");
    }

    @Test
    public void testMultiWidthRequiredResolvesAndAdvancesByWidth() throws Exception {
        StandaloneCommandManager manager = newManager();
        manager.registerResolver(Point.class, new ArgumentResolver<Object, Point>() {
            @Override
            public Point resolve(Object sender, String[] args, String current) {
                assertEquals(2, args.length, "Multi-width resolver must receive exactly width arguments");
                return new Point(Double.parseDouble(args[0]), Double.parseDouble(args[1]));
            }

            @Override
            public int getWidth() {
                return 2;
            }
        });

        String[] args = {"1.0", "2.0", "trailing"};
        int[] indexHolder = {0};

        Point result = manager.resolveParameter(SENDER, Point.class, args, indexHolder, "pt", false, null);

        assertEquals(1.0, result.x);
        assertEquals(2.0, result.y);
        assertEquals(2, indexHolder[0], "indexHolder must advance by the resolver's width");
    }

    @Test
    public void testRequiredWithInsufficientArgumentsThrowsAndNamesParam() {
        StandaloneCommandManager manager = newManager();
        manager.registerResolver(Point.class, new ArgumentResolver<Object, Point>() {
            @Override
            public Point resolve(Object sender, String[] args, String current) {
                fail("Resolver must not be invoked when required arguments are missing");
                return null;
            }

            @Override
            public int getWidth() {
                return 2;
            }
        });

        String[] args = {"1.0"};
        int[] indexHolder = {0};

        CommandException ex = assertThrows(CommandException.class, () ->
                manager.resolveParameter(SENDER, Point.class, args, indexHolder, "pt", false, null));

        assertTrue(ex.getMessage().contains("pt"), "Missing-argument message must mention the parameter name");
        assertEquals(0, indexHolder[0], "indexHolder must be untouched on required-missing failure");
    }

    @Test
    public void testOptionalWithNoDefaultReturnsNullAndLeavesIndexUntouched() throws Exception {
        StandaloneCommandManager manager = newManager();
        manager.registerResolver(Point.class, new ArgumentResolver<Object, Point>() {
            @Override
            public Point resolve(Object sender, String[] args, String current) {
                fail("Resolver must not be invoked when optional and no default is provided");
                return null;
            }

            @Override
            public int getWidth() {
                return 2;
            }
        });

        String[] args = {};
        int[] indexHolder = {0};

        Point result = manager.resolveParameter(SENDER, Point.class, args, indexHolder, "pt", true, null);

        assertNull(result, "Optional with no default and missing args must resolve to null");
        assertEquals(0, indexHolder[0], "indexHolder must not advance when no arguments are consumed");
    }

    @Test
    public void testOptionalWithDefaultInvokesResolverAndLeavesIndexUntouched() throws Exception {
        StandaloneCommandManager manager = newManager();
        String[] capturedArgs = {null};
        manager.registerResolver(String.class, (sender, args, current) -> {
            capturedArgs[0] = args[0];
            assertSame(SENDER, sender);
            assertEquals("def", current, "current must be the default value string");
            return "resolved:" + current;
        });

        String[] args = {};
        int[] indexHolder = {0};

        String result = manager.resolveParameter(SENDER, String.class, args, indexHolder, "name", true, "def");

        assertEquals("resolved:def", result);
        assertEquals("def", capturedArgs[0], "Resolver must receive the default value in a single-element args array");
        assertEquals(0, indexHolder[0], "indexHolder must not advance when the value came from the default");
    }

    @Test
    public void testNativeSenderIsPassedUnchangedToResolver() throws Exception {
        StandaloneCommandManager manager = newManager();
        Object[] capturedSender = {null};
        manager.registerResolver(String.class, (sender, args, current) -> {
            capturedSender[0] = sender;
            return current;
        });

        Object mySender = new Object();
        manager.resolveParameter(mySender, String.class, new String[]{"v"}, new int[]{0}, "name", false, null);

        assertSame(mySender, capturedSender[0], "manager must forward the exact native sender to the resolver");
    }

    @Test
    public void testHierarchyFallbackResolvesImplementingType() throws Exception {
        StandaloneCommandManager manager = newManager();
        manager.registerResolver(TestCommand.CustomInterface.class,
                (sender, args, current) -> new TestCommand.CustomImpl("hier:" + current));

        // Resolve a concrete implementing class that has no exact-match resolver
        TestCommand.CustomImpl result = manager.resolveParameter(
                SENDER, TestCommand.CustomImpl.class, new String[]{"hello"}, new int[]{0}, "c", false, null);

        assertNotNull(result);
        assertEquals("hier:hello", result.getName(),
                "When no exact-match resolver is registered, the interface hierarchy fallback must be used");
    }

    @Test
    public void testProviderFallbackResolvesUnregisteredType() throws Exception {
        StandaloneCommandManager manager = newManager();
        manager.registerProvider(new ArgumentResolverProvider<Object>() {
            @SuppressWarnings("unchecked")
            @Override
            public ArgumentResolver<Object, ?> getResolver(Class<?> type) {
                if (type == Point.class) {
                    return (ArgumentResolver<Object, Point>) (sender, args, current) -> new Point(99.0, 99.0);
                }
                return null;
            }
        });

        Point result = manager.resolveParameter(
                SENDER, Point.class, new String[]{"ignored"}, new int[]{0}, "pt", false, null);

        assertEquals(99.0, result.x);
        assertEquals(99.0, result.y);
    }

    @Test
    public void testSuggestDelegatesToRegisteredResolver() {
        StandaloneCommandManager manager = newManager();
        List<String> suggestions = Arrays.asList("alpha", "beta");
        manager.registerResolver(String.class, new ArgumentResolver<Object, String>() {
            @Override
            public String resolve(Object sender, String[] args, String current) {
                return current;
            }

            @Override
            public List<String> suggest(Object sender, String[] args, String current) {
                return suggestions;
            }
        });

        List<String> out = manager.getResolver(String.class).suggest(SENDER, new String[0], "a");
        assertIterableEquals(Arrays.asList("alpha", "beta"), out);
    }

    @Test
    public void testNoResolverRegisteredFallsBackToSenderWhenAssignable() throws Exception {
        StandaloneCommandManager manager = newManager();

        // With no resolver registered for Object, the default fallback returns the sender
        // when the requested type is assignable from the sender.
        Object result = manager.resolveParameter(
                SENDER, Object.class, new String[]{"ignored"}, new int[]{0}, "x", false, null);

        assertSame(SENDER, result,
                "When no resolver is registered and the sender is assignable to the requested type, "
                        + "the fallback must return the sender");
    }

    @Test
    public void testNoResolverRegisteredFallsBackThrowsIllegalArgumentExceptionWhenNotAssignable() {
        StandaloneCommandManager manager = newManager();

        // The default fallback resolver throws IllegalArgumentException (not CommandException)
        // when invoked with a type the sender cannot be cast to. resolveParameter propagates it
        // unchanged; the generated wrappers catch generic Exception, so this documents that
        // resolveParameter does not wrap it.
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () ->
                manager.resolveParameter(SENDER, Point.class, new String[]{"1"}, new int[]{0}, "pt", false, null));

        assertTrue(ex.getMessage().contains("No argument resolver registered for type"),
                "Default fallback message must mention the unregistered type");
    }

    @Test
    public void testIndexHolderAdvanceAffectsSubsequentRead() throws Exception {
        // Simulates the generated wrapper pattern: after resolving a width=2 Point,
        // the next built-in parameter reads from args[indexHolder[0]]. Verifying that
        // resolveParameter advances by exactly `width` keeps subsequent reads correct.
        StandaloneCommandManager manager = newManager();
        manager.registerResolver(Point.class, new ArgumentResolver<Object, Point>() {
            @Override
            public Point resolve(Object sender, String[] args, String current) {
                return new Point(Double.parseDouble(args[0]), Double.parseDouble(args[1]));
            }

            @Override
            public int getWidth() {
                return 2;
            }
        });

        String[] args = {"1.0", "2.0", "msg"};
        int[] indexHolder = {0};

        Point pt = manager.resolveParameter(SENDER, Point.class, args, indexHolder, "pt", false, null);
        assertEquals(1.0, pt.x);
        assertEquals(2.0, pt.y);
        assertEquals(2, indexHolder[0]);

        // Subsequent built-in parameter reads from args[indexHolder[0]] (generator's pattern)
        String next = args[indexHolder[0]];
        assertEquals("msg", next,
                "After a width=2 resolution, the next argument index must point past the consumed args");
    }
}