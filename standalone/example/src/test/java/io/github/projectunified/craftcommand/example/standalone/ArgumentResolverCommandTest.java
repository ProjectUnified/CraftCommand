package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.ArgumentResolver;
import io.github.projectunified.craftcommand.CommandManager;
import io.github.projectunified.craftcommand.example.standalone.ArgumentResolverCommand.*;
import io.github.projectunified.craftcommand.exception.CommandException;
import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static io.github.projectunified.craftcommand.example.standalone.TestHelpers.assertSuggestionsContain;
import static org.junit.jupiter.api.Assertions.*;

public class ArgumentResolverCommandTest extends AbstractStandaloneCommandTest {

    @Override
    protected void registerCommand() {
        // 1. Single-width TargetUser resolver with suggestions
        manager.registerResolver(TargetUser.class, new ArgumentResolver<Object, TargetUser>() {
            @Override
            public TargetUser resolve(Object sender, String[] current, String[] context) {
                return new TargetUser(current[0]);
            }

            @Override
            public List<String> suggest(Object sender, String[] current, String[] context) {
                return CommandManager.filterSuggestions(Arrays.asList("Alice", "Bob", "Charlie"), current.length > 0 ? current[0] : "");
            }
        });

        // 2. Multi-width Vec2D resolver (width 2)
        manager.registerResolver(Vec2D.class, new ArgumentResolver<Object, Vec2D>() {
            @Override
            public Vec2D resolve(Object sender, String[] current, String[] context) {
                if (current.length < 2) {
                    if (current.length == 1 && current[0].contains(" ")) {
                        String[] split = current[0].split(" ");
                        return new Vec2D(Integer.parseInt(split[0]), Integer.parseInt(split[1]));
                    }
                    throw new IllegalArgumentException("Need 2 coordinates for Vec2D");
                }
                return new Vec2D(Integer.parseInt(current[0]), Integer.parseInt(current[1]));
            }

            @Override
            public int getWidth() {
                return 2;
            }

            @Override
            public List<String> suggest(Object sender, String[] current, String[] context) {
                return Arrays.asList("0 0", "10 20");
            }
        });

        // 3. Multi-width Vec3D resolver (width 3)
        manager.registerResolver(Vec3D.class, new ArgumentResolver<Object, Vec3D>() {
            @Override
            public Vec3D resolve(Object sender, String[] current, String[] context) {
                return new Vec3D(Integer.parseInt(current[0]), Integer.parseInt(current[1]), Integer.parseInt(current[2]));
            }

            @Override
            public int getWidth() {
                return 3;
            }

            @Override
            public List<String> suggest(Object sender, String[] current, String[] context) {
                return Arrays.asList("0 0 0", "10 20 30");
            }
        });

        // 4. Global sender resolver for SessionToken
        manager.registerSenderResolver(SessionToken.class, sender -> new SessionToken(sender, "token-for-" + sender.toString()));

        // 5. Interface/Hierarchy BaseTag resolver
        manager.registerResolver(BaseTag.class, (sender, current, context) -> new CustomTag(current[0]));

        // 6. StrictPort resolver throwing CommandException on invalid input
        manager.registerResolver(StrictPort.class, (sender, current, context) -> {
            int port = Integer.parseInt(current[0]);
            if (port < 1 || port > 65535) {
                throw new CommandException("Port must be between 1 and 65535: " + port);
            }
            return new StrictPort(port);
        });

        manager.register(new ArgumentResolverCommand());
    }

    @Override
    protected String getCommandName() {
        return "resolvertest";
    }

    @Test
    public void testSingleWidthResolver() {
        assertTrue(execute("user", "Alice"));
        assertEquals(List.of("user=Alice"), sender.getMessages());
    }

    @Test
    public void testSingleWidthResolverSuggestions() {
        List<String> suggestions = tabComplete("user", "A");
        assertEquals(List.of("Alice"), suggestions);

        List<String> allSuggestions = tabComplete("user", "");
        assertSuggestionsContain(allSuggestions, "Alice", "Bob", "Charlie");
    }

    @Test
    public void testMultiWidthVec2DResolver() {
        assertTrue(execute("vec2", "15", "30"));
        assertEquals(List.of("vec2=15,30"), sender.getMessages());
    }

    @Test
    public void testMultiWidthVec3DResolver() {
        assertTrue(execute("vec3", "1", "2", "3"));
        assertEquals(List.of("vec3=1,2,3"), sender.getMessages());
    }

    @Test
    public void testZeroWidthSessionTokenResolver() {
        assertTrue(execute("session", "login"));
        assertEquals(List.of("session=token-for-test,action=login"), sender.getMessages());
    }

    @Test
    public void testHierarchyResolverLookup() {
        assertTrue(execute("tag", "alpha-tag"));
        assertEquals(List.of("tag=alpha-tag"), sender.getMessages());
    }

    @Test
    public void testOptionalResolverWithDefault() {
        assertTrue(execute("optvec"));
        assertEquals(List.of("optvec=10,20"), sender.getMessages());

        sender.getMessages().clear();
        assertTrue(execute("optvec", "50", "60"));
        assertEquals(List.of("optvec=50,60"), sender.getMessages());
    }

    @Test
    public void testOptionalSingleWidthResolver() {
        assertTrue(execute("optuser"));
        assertEquals(List.of("optuser=guest"), sender.getMessages());

        sender.getMessages().clear();
        assertTrue(execute("optuser", "Charlie"));
        assertEquals(List.of("optuser=Charlie"), sender.getMessages());
    }

    @Test
    public void testMultipleCombinedResolvers() {
        // user: Alice (1), origin: 10 20 (2), target: 1 2 3 (3), speed: 99 (1) -> 7 args total
        assertTrue(execute("combo", "Alice", "10", "20", "1", "2", "3", "99"));
        assertEquals(List.of("combo:user=Alice,origin=10,20,target=1,2,3,speed=99"), sender.getMessages());

        // with default speed (5) -> 6 args total
        sender.getMessages().clear();
        assertTrue(execute("combo", "Bob", "0", "0", "100", "200", "300"));
        assertEquals(List.of("combo:user=Bob,origin=0,0,target=100,200,300,speed=5"), sender.getMessages());
    }

    @Test
    public void testResolverValidationSuccess() {
        assertTrue(execute("port", "8080"));
        assertEquals(List.of("port=8080"), sender.getMessages());
    }

    @Test
    public void testResolverValidationFailure() {
        assertThrows(RuntimeException.class, () -> execute("port", "99999"));
    }

    @Test
    public void testMissingRequiredArgumentsForMultiWidth() {
        // vec3 requires 3 arguments, only 2 provided
        assertThrows(RuntimeException.class, () -> execute("vec3", "1", "2"));
    }
}
