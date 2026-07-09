package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.bukkit.BukkitCommandManager;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class AllFeaturesCommandTest extends AbstractBukkitCommandTest {

    @Override
    protected void registerCommand() {
        new BukkitCommandManager(MockBukkit.createMockPlugin()).register(new AllFeaturesCommand());
    }

    private PlayerMock addPlayer(String... perms) {
        PlayerMock player = server.addPlayer();
        for (String perm : perms) {
            player.addAttachment(MockBukkit.createMockPlugin(), perm, true);
        }
        return player;
    }

    private void execute(String sub, String... args) {
        PlayerMock player = addPlayer("features.base");
        String[] full = new String[args.length + 1];
        full[0] = sub;
        System.arraycopy(args, 0, full, 1, args.length);
        server.getCommandMap().getCommand("features").execute(player, "features", full);
    }

    private PlayerMock executeAs(String sub, String... args) {
        PlayerMock player = addPlayer("features.base");
        String[] full = new String[args.length + 1];
        full[0] = sub;
        System.arraycopy(args, 0, full, 1, args.length);
        server.getCommandMap().getCommand("features").execute(player, "features", full);
        return player;
    }

    private List<String> tabComplete(String... args) {
        PlayerMock player = addPlayer("features.base");
        return server.getCommandMap().getCommand("features").tabComplete(player, "features", args);
    }

    // ── Default Action ──

    @Test
    public void testDefaultNoTarget() {
        execute("features");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{});
        assertEquals("Default: no target", player.nextMessage());
    }

    @Test
    public void testDefaultWithTarget() {
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{player.getName()});
        assertEquals("Default: " + player.getName(), player.nextMessage());
    }

    // ── Single-Param Annotation Stacking ──

    @Test
    public void testHello() {
        execute("hello", "world");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"hello", "world"});
        assertEquals("Hello, world!", player.nextMessage());
    }

    @Test
    public void testAdd() {
        execute("add", "10", "20");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"add", "10", "20"});
        assertEquals("result=30", player.nextMessage());
    }

    @Test
    public void testAddOutOfRangeLow() {
        execute("add", "-1", "20");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"add", "-1", "20"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testAddOutOfRangeHigh() {
        execute("add", "101", "20");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"add", "101", "20"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testRangeDefault() {
        execute("range");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"range"});
        assertEquals("range=50", player.nextMessage());
    }

    @Test
    public void testRangeWithValue() {
        execute("range", "75");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"range", "75"});
        assertEquals("range=75", player.nextMessage());
    }

    @Test
    public void testRangeOutOfRange() {
        execute("range", "101");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"range", "101"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testValidatedDefault() {
        execute("validated");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"validated"});
        assertEquals("validated=50", player.nextMessage());
    }

    @Test
    public void testValidatedEven() {
        execute("validated", "50");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"validated", "50"});
        assertEquals("validated=50", player.nextMessage());
    }

    @Test
    public void testValidatedOdd() {
        execute("validated", "51");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"validated", "51"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testEcho() {
        execute("echo", "hello world");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"echo", "hello world"});
        assertEquals("echo=hello world", player.nextMessage());
    }

    @Test
    public void testNamedEcho() {
        execute("namedecho", "hello world");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"namedecho", "hello world"});
        assertEquals("namedecho=hello world", player.nextMessage());
    }

    @Test
    public void testSuggestEcho() {
        execute("suggestecho", "hello world");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"suggestecho", "hello world"});
        assertEquals("suggestecho=hello world", player.nextMessage());
    }

    @Test
    public void testSuggestEchoTabComplete() {
        List<String> suggestions = tabComplete("suggestecho", "");
        assertTrue(suggestions.contains("red"));
        assertTrue(suggestions.contains("green"));
        assertTrue(suggestions.contains("blue"));
        assertTrue(suggestions.contains("yellow"));
    }

    @Test
    public void testFullGreedyDefault() {
        execute("fullgreedy");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"fullgreedy"});
        assertEquals("fullgreedy=red", player.nextMessage());
    }

    @Test
    public void testFullGreedyWithValue() {
        execute("fullgreedy", "blue");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"fullgreedy", "blue"});
        assertEquals("fullgreedy=blue", player.nextMessage());
    }

    @Test
    public void testFullGreedyTabComplete() {
        List<String> suggestions = tabComplete("fullgreedy", "");
        assertTrue(suggestions.contains("red"));
        assertTrue(suggestions.contains("green"));
        assertTrue(suggestions.contains("blue"));
        assertTrue(suggestions.contains("yellow"));
    }

    @Test
    public void testMode() {
        execute("mode", "normal");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"mode", "normal"});
        assertEquals("mode=normal", player.nextMessage());
    }

    @Test
    public void testModeTabComplete() {
        List<String> suggestions = tabComplete("mode", "");
        assertTrue(suggestions.contains("normal"));
        assertTrue(suggestions.contains("silent"));
        assertTrue(suggestions.contains("instant"));
    }

    @Test
    public void testNamedMode() {
        execute("namedmode", "silent");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"namedmode", "silent"});
        assertEquals("namedmode=silent", player.nextMessage());
    }

    @Test
    public void testNamedModeTabComplete() {
        List<String> suggestions = tabComplete("namedmode", "");
        assertTrue(suggestions.contains("normal"));
        assertTrue(suggestions.contains("silent"));
        assertTrue(suggestions.contains("instant"));
    }

    @Test
    public void testDefaultMode() {
        execute("defaultmode");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"defaultmode"});
        assertEquals("defaultmode=normal", player.nextMessage());
    }

    @Test
    public void testDefaultModeWithValue() {
        execute("defaultmode", "instant");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"defaultmode", "instant"});
        assertEquals("defaultmode=instant", player.nextMessage());
    }

    @Test
    public void testDefaultModeTabComplete() {
        List<String> suggestions = tabComplete("defaultmode", "");
        assertTrue(suggestions.contains("normal"));
        assertTrue(suggestions.contains("silent"));
        assertTrue(suggestions.contains("instant"));
    }

    // ── Resolver Commands ──

    @Test
    public void testResolve() {
        execute("resolve", "3.0", "4.0");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolve", "3.0", "4.0"});
        assertEquals("resolve=3.0,4.0", player.nextMessage());
    }

    @Test
    public void testResolveDefault() {
        execute("resolveDefault", "3.0");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveDefault", "3.0"});
        assertEquals("resolveDefault=3.0,0.0", player.nextMessage());
    }

    @Test
    public void testResolveDefaultBothArgs() {
        execute("resolveDefault", "3.0", "5.0");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveDefault", "3.0", "5.0"});
        assertEquals("resolveDefault=3.0,5.0", player.nextMessage());
    }

    @Test
    public void testResolveSuggest() {
        execute("resolveSuggest", "normal", "3.0");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveSuggest", "normal", "3.0"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testResolveSuggestTabComplete() {
        List<String> suggestions = tabComplete("resolveSuggest", "");
        assertTrue(suggestions.contains("normal"));
        assertTrue(suggestions.contains("silent"));
        assertTrue(suggestions.contains("instant"));
    }

    @Test
    public void testResolveNamed() {
        execute("resolveNamed", "3.0", "4.0");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveNamed", "3.0", "4.0"});
        assertEquals("resolveNamed=3.0,4.0", player.nextMessage());
    }

    @Test
    public void testResolveClamped() {
        execute("resolveClamped", "50");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveClamped", "50"});
        assertEquals("resolveClamped=50", player.nextMessage());
    }

    @Test
    public void testResolveClampedOutOfRange() {
        execute("resolveClamped", "101");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveClamped", "101"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testResolveClampedNegative() {
        execute("resolveClamped", "-1");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveClamped", "-1"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testResolveGreedy() {
        execute("resolveGreedy", "hello world");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveGreedy", "hello world"});
        assertEquals("resolveGreedy=hello world", player.nextMessage());
    }

    @Test
    public void testSameSender() {
        execute("sameSender", "3.0", "4.0");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"sameSender", "3.0", "4.0"});
        assertEquals("sameSender=3.0,4.0", player.nextMessage());
    }

    // ── Multi-Param Cross-Parameter Interactions ──

    @Test
    public void testResolveAndSuggest() {
        execute("resolveAndSuggest", "3.0", "4.0", "normal");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveAndSuggest", "3.0", "4.0", "normal"});
        assertEquals("resolveAndSuggest=3.0,4.0,normal", player.nextMessage());
    }

    @Test
    public void testResolveAndSuggestTabComplete() {
        List<String> suggestions = tabComplete("resolveAndSuggest", "3.0", "4.0", "");
        assertTrue(suggestions.contains("normal"));
        assertTrue(suggestions.contains("silent"));
        assertTrue(suggestions.contains("instant"));
    }

    @Test
    public void testResolveAndDefault() {
        execute("resolveAndDefault", "3.0", "4.0");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveAndDefault", "3.0", "4.0"});
        assertEquals("resolveAndDefault=3.0,4.0,normal", player.nextMessage());
    }

    @Test
    public void testResolveAndDefaultWithValue() {
        execute("resolveAndDefault", "3.0", "4.0", "silent");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveAndDefault", "3.0", "4.0", "silent"});
        assertEquals("resolveAndDefault=3.0,4.0,silent", player.nextMessage());
    }

    @Test
    public void testResolveAndValidate() {
        execute("resolveAndValidate", "3.0", "4.0");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveAndValidate", "3.0", "4.0"});
        assertEquals("resolveAndValidate=3.0,4.0,50", player.nextMessage());
    }

    @Test
    public void testResolveAndValidateWithValue() {
        execute("resolveAndValidate", "3.0", "4.0", "75");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveAndValidate", "3.0", "4.0", "75"});
        assertEquals("resolveAndValidate=3.0,4.0,75", player.nextMessage());
    }

    @Test
    public void testResolveAndValidateOutOfRange() {
        execute("resolveAndValidate", "3.0", "4.0", "101");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolveAndValidate", "3.0", "4.0", "101"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testSuggestAndDefault() {
        execute("suggestAndDefault");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"suggestAndDefault"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testSuggestAndDefaultWithMode() {
        execute("suggestAndDefault", "normal");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"suggestAndDefault", "normal"});
        assertEquals("suggestAndDefault=normal,50", player.nextMessage());
    }

    @Test
    public void testSuggestAndDefaultWithBoth() {
        execute("suggestAndDefault", "normal", "25");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"suggestAndDefault", "normal", "25"});
        assertEquals("suggestAndDefault=normal,25", player.nextMessage());
    }

    @Test
    public void testSuggestAndDefaultTabComplete() {
        List<String> suggestions = tabComplete("suggestAndDefault", "");
        assertTrue(suggestions.contains("normal"));
        assertTrue(suggestions.contains("silent"));
        assertTrue(suggestions.contains("instant"));
    }

    @Test
    public void testDefaultAndGreedy() {
        execute("defaultAndGreedy");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"defaultAndGreedy"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testDefaultAndGreedyWithGreeting() {
        execute("defaultAndGreedy", "hello", "world");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"defaultAndGreedy", "hello", "world"});
        assertEquals("defaultAndGreedy=hello,world", player.nextMessage());
    }

    @Test
    public void testDefaultAndGreedyWithMultiWord() {
        execute("defaultAndGreedy", "hello", "how are you");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"defaultAndGreedy", "hello", "how are you"});
        assertEquals("defaultAndGreedy=hello,how are you", player.nextMessage());
    }

    @Test
    public void testTripleInteract() {
        execute("tripleInteract", "3.0", "4.0");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"tripleInteract", "3.0", "4.0"});
        assertEquals("tripleInteract=3.0,4.0,red,50", player.nextMessage());
    }

    @Test
    public void testTripleInteractWithAll() {
        execute("tripleInteract", "3.0", "4.0", "blue", "75");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"tripleInteract", "3.0", "4.0", "blue", "75"});
        assertEquals("tripleInteract=3.0,4.0,blue,75", player.nextMessage());
    }

    @Test
    public void testTripleInteractTabCompleteColor() {
        List<String> suggestions = tabComplete("tripleInteract", "3.0", "4.0", "");
        assertTrue(suggestions.contains("red"));
        assertTrue(suggestions.contains("green"));
        assertTrue(suggestions.contains("blue"));
        assertTrue(suggestions.contains("yellow"));
    }

    // ── Nested Class (Panel Commands with @Permission) ──

    @Test
    public void testPanelDefault() {
        execute("panel");
        PlayerMock player = addPlayer("features.base", "features.panel");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"panel"});
        assertEquals("panel open", player.nextMessage());
    }

    @Test
    public void testPanelSpawn() {
        execute("panel", "spawn", "world");
        PlayerMock player = addPlayer("features.base", "features.panel");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"panel", "spawn", "world"});
        assertEquals("spawn=world", player.nextMessage());
    }

    @Test
    public void testPanelInfo() {
        execute("panel", "info");
        PlayerMock player = addPlayer("features.base", "features.panel");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"panel", "info"});
        assertEquals("info=" + player.getName(), player.nextMessage());
    }

    @Test
    public void testPanelTabComplete() {
        List<String> suggestions = tabComplete("panel", "");
        assertTrue(suggestions.contains("spawn"));
        assertTrue(suggestions.contains("info"));
    }

    @Test
    public void testPanelPermissionDenied() {
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"panel"});
        assertNotNull(player.nextMessage());
    }

    // ── Permission Variations ──

    @Test
    public void testAdminAllowed() {
        PlayerMock player = addPlayer("features.base", "features.admin");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"admin"});
        assertEquals("admin panel", player.nextMessage());
    }

    @Test
    public void testAdminDenied() {
        PlayerMock player = addPlayer();
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"admin"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testSecretAllowed() {
        PlayerMock player = addPlayer("features.base", "features.secret");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"secret"});
        assertEquals("secret area", player.nextMessage());
    }

    @Test
    public void testSecretDeniedCustomMessage() {
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"secret"});
        assertEquals("Access denied to secret area!", player.nextMessage());
    }

    @Test
    public void testBasePermissionDenied() {
        PlayerMock player = addPlayer();
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"hello", "world"});
        assertNotNull(player.nextMessage());
    }

    // ── Tab Completion ──

    @Test
    public void testTabCompleteSubcommands() {
        List<String> suggestions = tabComplete("");
        assertTrue(suggestions.contains("hello"));
        assertTrue(suggestions.contains("add"));
        assertTrue(suggestions.contains("range"));
        assertTrue(suggestions.contains("echo"));
        assertTrue(suggestions.contains("mode"));
        assertTrue(suggestions.contains("resolve"));
        assertTrue(suggestions.contains("panel"));
        assertTrue(suggestions.contains("admin"));
        assertTrue(suggestions.contains("secret"));
    }

    @Test
    public void testTabCompleteModeSuggestions() {
        List<String> suggestions = tabComplete("mode", "");
        assertTrue(suggestions.contains("normal"));
        assertTrue(suggestions.contains("silent"));
        assertTrue(suggestions.contains("instant"));
    }

    @Test
    public void testTabCompleteModeFilter() {
        List<String> suggestions = tabComplete("mode", "n");
        assertTrue(suggestions.contains("normal"));
        assertEquals(1, suggestions.size());
    }

    @Test
    public void testTabCompleteNamedModeSuggestions() {
        List<String> suggestions = tabComplete("namedmode", "");
        assertTrue(suggestions.contains("normal"));
        assertTrue(suggestions.contains("silent"));
        assertTrue(suggestions.contains("instant"));
    }

    @Test
    public void testTabCompleteDefaultModeSuggestions() {
        List<String> suggestions = tabComplete("defaultmode", "");
        assertTrue(suggestions.contains("normal"));
        assertTrue(suggestions.contains("silent"));
        assertTrue(suggestions.contains("instant"));
    }

    @Test
    public void testTabCompleteSuggestEchoSuggestions() {
        List<String> suggestions = tabComplete("suggestecho", "");
        assertTrue(suggestions.contains("red"));
        assertTrue(suggestions.contains("green"));
        assertTrue(suggestions.contains("blue"));
        assertTrue(suggestions.contains("yellow"));
    }

    @Test
    public void testTabCompleteFullGreedySuggestions() {
        List<String> suggestions = tabComplete("fullgreedy", "");
        assertTrue(suggestions.contains("red"));
        assertTrue(suggestions.contains("green"));
        assertTrue(suggestions.contains("blue"));
        assertTrue(suggestions.contains("yellow"));
    }

    @Test
    public void testTabCompleteResolveSuggestSuggestions() {
        List<String> suggestions = tabComplete("resolveSuggest", "");
        assertTrue(suggestions.contains("normal"));
        assertTrue(suggestions.contains("silent"));
        assertTrue(suggestions.contains("instant"));
    }

    @Test
    public void testTabCompleteResolveAndSuggestSuggestions() {
        List<String> suggestions = tabComplete("resolveAndSuggest", "3.0", "4.0", "");
        assertTrue(suggestions.contains("normal"));
        assertTrue(suggestions.contains("silent"));
        assertTrue(suggestions.contains("instant"));
    }

    @Test
    public void testTabCompleteSuggestAndDefaultSuggestions() {
        List<String> suggestions = tabComplete("suggestAndDefault", "");
        assertTrue(suggestions.contains("normal"));
        assertTrue(suggestions.contains("silent"));
        assertTrue(suggestions.contains("instant"));
    }

    @Test
    public void testTabCompleteTripleInteractSuggestions() {
        List<String> suggestions = tabComplete("tripleInteract", "3.0", "4.0", "");
        assertTrue(suggestions.contains("red"));
        assertTrue(suggestions.contains("green"));
        assertTrue(suggestions.contains("blue"));
        assertTrue(suggestions.contains("yellow"));
    }

    // ── Error Cases ──

    @Test
    public void testMissingArgs() {
        execute("hello");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"hello"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testInvalidNumber() {
        execute("add", "abc", "20");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"add", "abc", "20"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testPermissionDeniedMessage() {
        PlayerMock player = addPlayer();
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"admin"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testSecretPermissionDeniedMessage() {
        PlayerMock player = addPlayer();
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"secret"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testResolveMissingArgs() {
        execute("resolve", "3.0");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"resolve", "3.0"});
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testAddMissingSecondArg() {
        execute("add", "10");
        PlayerMock player = addPlayer("features.base");
        server.getCommandMap().getCommand("features").execute(player, "features", new String[]{"add", "10"});
        assertNotNull(player.nextMessage());
    }
}
