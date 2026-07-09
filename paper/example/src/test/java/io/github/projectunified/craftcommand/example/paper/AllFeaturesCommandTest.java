package io.github.projectunified.craftcommand.example.paper;

import com.mojang.brigadier.CommandDispatcher;
import com.mojang.brigadier.suggestion.Suggestion;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

public class AllFeaturesCommandTest extends AbstractPaperCommandTest {

    private CommandDispatcher<CommandSourceStack> dispatcher;

    @Override
    @BeforeEach
    public void setUp() {
        super.setUp();
        dispatcher = register(AllFeaturesCommand.class);
    }

    private PlayerMock addPlayer() {
        PlayerMock player = server.addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "features.base", true);
        return player;
    }

    private List<String> suggestions(String input, PlayerMock player) {
        com.mojang.brigadier.ParseResults<CommandSourceStack> parse =
                dispatcher.parse(input, source(player));
        CompletableFuture<com.mojang.brigadier.suggestion.Suggestions> future =
                dispatcher.getCompletionSuggestions(parse);
        return future.join().getList().stream()
                .map(Suggestion::getText)
                .collect(Collectors.toList());
    }

    // ═══════════════════════════════════════════════════════════════
    // Default Action
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testDefaultWithTarget() throws Exception {
        PlayerMock player = addPlayer();
        PlayerMock target = server.addPlayer("TargetPlayer");
        dispatcher.execute("features TargetPlayer", source(player));
        assertEquals("Default: TargetPlayer", player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Single-Param Annotation Stacking
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testHello() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features hello World", source(player));
        assertEquals("Hello, World!", player.nextMessage());
    }

    @Test
    public void testAdd() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features add 10 20", source(player));
        assertEquals("result=30", player.nextMessage());
    }

    @Test
    public void testAddBelowMin() {
        PlayerMock player = addPlayer();
        assertThrows(Exception.class, () ->
                dispatcher.execute("features add -1 20", source(player)));
    }

    @Test
    public void testAddAboveMax() {
        PlayerMock player = addPlayer();
        assertThrows(Exception.class, () ->
                dispatcher.execute("features add 200 20", source(player)));
    }

    @Test
    public void testRangeCustom() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features range 25", source(player));
        assertEquals("range=25", player.nextMessage());
    }

    @Test
    public void testRangeBelowMin() {
        PlayerMock player = addPlayer();
        assertThrows(Exception.class, () ->
                dispatcher.execute("features range -1", source(player)));
    }

    @Test
    public void testRangeAboveMax() {
        PlayerMock player = addPlayer();
        assertThrows(Exception.class, () ->
                dispatcher.execute("features range 150", source(player)));
    }

    @Test
    public void testValidatedEven() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features validated 42", source(player));
        assertEquals("validated=42", player.nextMessage());
    }

    @Test
    public void testValidatedOdd() {
        PlayerMock player = addPlayer();
        assertThrows(Exception.class, () ->
                dispatcher.execute("features validated 43", source(player)));
    }

    @Test
    public void testValidatedBelowMin() {
        PlayerMock player = addPlayer();
        assertThrows(Exception.class, () ->
                dispatcher.execute("features validated -2", source(player)));
    }

    @Test
    public void testEcho() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features echo Hello World", source(player));
        assertEquals("echo=Hello World", player.nextMessage());
    }

    @Test
    public void testNamedEcho() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features namedecho Hello World", source(player));
        assertEquals("namedecho=Hello World", player.nextMessage());
    }

    @Test
    public void testSuggestEcho() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features suggestecho red green", source(player));
        assertEquals("suggestecho=red green", player.nextMessage());
    }

    @Test
    public void testFullGreedyCustom() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features fullgreedy blue test", source(player));
        assertEquals("fullgreedy=blue test", player.nextMessage());
    }

    @Test
    public void testMode() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features mode normal", source(player));
        assertEquals("mode=normal", player.nextMessage());
    }

    @Test
    public void testNamedMode() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features namedmode silent", source(player));
        assertEquals("namedmode=silent", player.nextMessage());
    }

    @Test
    public void testDefaultModeCustom() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features defaultmode instant", source(player));
        assertEquals("defaultmode=instant", player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Resolver Commands
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testResolve() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features resolve 1.5 2.5", source(player));
        assertEquals("resolve=1.5,2.5", player.nextMessage());
    }

    @Test
    public void testResolveDefault() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features resolveDefault 3.0 4.0", source(player));
        assertEquals("resolveDefault=3.0,4.0", player.nextMessage());
    }

    @Test
    public void testResolveSuggest() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features resolveSuggest normal 5.0", source(player));
        assertEquals("resolveSuggest=5.0,-17.0", player.nextMessage());
    }

    @Test
    public void testResolveNamed() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features resolveNamed 10.0 20.0", source(player));
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testResolveGreedy() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features resolveGreedy some text here", source(player));
        assertEquals("resolveGreedy=some text here", player.nextMessage());
    }

    @Test
    public void testSameSender() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features sameSender 7.0 8.0", source(player));
        assertEquals("sameSender=7.0,8.0", player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Multi-Param Cross-Parameter Interactions
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testResolveAndSuggest() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features resolveAndSuggest 1.0 2.0 silent", source(player));
        assertEquals("resolveAndSuggest=1.0,2.0,silent", player.nextMessage());
    }

    @Test
    public void testResolveAndDefaultCustom() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features resolveAndDefault 3.0 4.0 instant", source(player));
        assertEquals("resolveAndDefault=3.0,4.0,instant", player.nextMessage());
    }

    @Test
    public void testResolveAndValidate() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features resolveAndValidate 1.0 2.0 50", source(player));
        assertEquals("resolveAndValidate=1.0,2.0,50", player.nextMessage());
    }

    @Test
    public void testSuggestAndDefault() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features suggestAndDefault normal 50", source(player));
        assertEquals("suggestAndDefault=normal,50", player.nextMessage());
    }

    @Test
    public void testDefaultAndGreedy() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features defaultAndGreedy hello world", source(player));
        assertEquals("defaultAndGreedy=hello,world", player.nextMessage());
    }

    @Test
    public void testDefaultAndGreedyBoth() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features defaultAndGreedy hi there friend", source(player));
        assertEquals("defaultAndGreedy=hi,there friend", player.nextMessage());
    }

    @Test
    public void testTripleInteract() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features tripleInteract 5.0 6.0 blue 75", source(player));
        assertEquals("tripleInteract=5.0,6.0,blue,75", player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Nested Subcommand Class (Panel)
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testPanelDefault() throws Exception {
        PlayerMock player = addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "features.panel", true);
        dispatcher.execute("features panel", source(player));
        assertEquals("panel open", player.nextMessage());
    }

    @Test
    public void testPanelDefaultDenied() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features panel", source(player));
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testPanelSpawn() throws Exception {
        PlayerMock player = addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "features.panel", true);
        dispatcher.execute("features panel spawn world_nether", source(player));
        assertEquals("spawn=world_nether", player.nextMessage());
    }

    @Test
    public void testPanelInfo() throws Exception {
        PlayerMock player = addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "features.panel", true);
        dispatcher.execute("features panel info", source(player));
        assertEquals("info=" + player.getName(), player.nextMessage());
    }

    @Test
    public void testPanelSpawnDenied() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features panel spawn world", source(player));
        assertNotNull(player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Permission Variations
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testAdminAllowed() throws Exception {
        PlayerMock player = addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "features.admin", true);
        dispatcher.execute("features admin", source(player));
        assertEquals("admin panel", player.nextMessage());
    }

    @Test
    public void testAdminDenied() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features admin", source(player));
        assertNotNull(player.nextMessage());
    }

    @Test
    public void testSecretCustomMessage() throws Exception {
        PlayerMock player = addPlayer();
        dispatcher.execute("features secret", source(player));
        String msg = player.nextMessage();
        assertNotNull(msg);
        assertTrue(msg.contains("Access denied to secret area!"));
    }

    @Test
    public void testSecretAllowed() throws Exception {
        PlayerMock player = addPlayer();
        player.addAttachment(MockBukkit.createMockPlugin(), "features.secret", true);
        dispatcher.execute("features secret", source(player));
        assertEquals("secret area", player.nextMessage());
    }

    // ═══════════════════════════════════════════════════════════════
    // Tab Completion
    // ═══════════════════════════════════════════════════════════════

    @Test
    public void testTopLevelSuggestions() {
        PlayerMock player = addPlayer();
        List<String> cmds = suggestions("features ", player);
        assertTrue(cmds.contains("hello"));
        assertTrue(cmds.contains("add"));
        assertTrue(cmds.contains("range"));
        assertTrue(cmds.contains("validated"));
        assertTrue(cmds.contains("echo"));
        assertTrue(cmds.contains("namedecho"));
        assertTrue(cmds.contains("suggestecho"));
        assertTrue(cmds.contains("fullgreedy"));
        assertTrue(cmds.contains("mode"));
        assertTrue(cmds.contains("namedmode"));
        assertTrue(cmds.contains("defaultmode"));
        assertTrue(cmds.contains("resolve"));
        assertTrue(cmds.contains("resolveDefault"));
        assertTrue(cmds.contains("resolveSuggest"));
        assertTrue(cmds.contains("resolveNamed"));
        assertTrue(cmds.contains("resolveClamped"));
        assertTrue(cmds.contains("resolveGreedy"));
        assertTrue(cmds.contains("sameSender"));
        assertTrue(cmds.contains("resolveAndSuggest"));
        assertTrue(cmds.contains("resolveAndDefault"));
        assertTrue(cmds.contains("resolveAndValidate"));
        assertTrue(cmds.contains("suggestAndDefault"));
        assertTrue(cmds.contains("defaultAndGreedy"));
        assertTrue(cmds.contains("tripleInteract"));
        assertTrue(cmds.contains("admin"));
        assertTrue(cmds.contains("secret"));
        assertTrue(cmds.contains("panel"));
    }

    @Test
    public void testModeSuggestions() {
        PlayerMock player = addPlayer();
        List<String> cmds = suggestions("features mode ", player);
        assertTrue(cmds.contains("normal"));
        assertTrue(cmds.contains("silent"));
        assertTrue(cmds.contains("instant"));
    }

    @Test
    public void testModeSuggestionFilter() {
        PlayerMock player = addPlayer();
        List<String> cmds = suggestions("features mode s", player);
        assertTrue(cmds.contains("silent"));
        assertFalse(cmds.contains("normal"));
    }

    @Test
    public void testNamedModeSuggestions() {
        PlayerMock player = addPlayer();
        List<String> cmds = suggestions("features namedmode ", player);
        assertTrue(cmds.contains("normal"));
        assertTrue(cmds.contains("silent"));
        assertTrue(cmds.contains("instant"));
    }

    @Test
    public void testDefaultModeSuggestions() {
        PlayerMock player = addPlayer();
        List<String> cmds = suggestions("features defaultmode ", player);
        assertTrue(cmds.contains("normal"));
        assertTrue(cmds.contains("silent"));
        assertTrue(cmds.contains("instant"));
    }

    @Test
    public void testSuggestEchoSuggestions() {
        PlayerMock player = addPlayer();
        List<String> cmds = suggestions("features suggestecho ", player);
        assertTrue(cmds.contains("red"));
        assertTrue(cmds.contains("green"));
        assertTrue(cmds.contains("blue"));
        assertTrue(cmds.contains("yellow"));
    }

    @Test
    public void testFullGreedySuggestions() {
        PlayerMock player = addPlayer();
        List<String> cmds = suggestions("features fullgreedy ", player);
        assertTrue(cmds.contains("red"));
        assertTrue(cmds.contains("green"));
        assertTrue(cmds.contains("blue"));
        assertTrue(cmds.contains("yellow"));
    }

    @Test
    public void testResolveAndSuggestSuggestions() {
        PlayerMock player = addPlayer();
        List<String> cmds = suggestions("features resolveAndSuggest 1.0 2.0 ", player);
        assertTrue(cmds.contains("normal"));
        assertTrue(cmds.contains("silent"));
        assertTrue(cmds.contains("instant"));
    }

    @Test
    public void testSuggestAndDefaultSuggestions() {
        PlayerMock player = addPlayer();
        List<String> cmds = suggestions("features suggestAndDefault ", player);
        assertTrue(cmds.contains("normal"));
        assertTrue(cmds.contains("silent"));
        assertTrue(cmds.contains("instant"));
    }

    @Test
    public void testTripleInteractSuggestions() {
        PlayerMock player = addPlayer();
        List<String> cmds = suggestions("features tripleInteract 5.0 6.0 ", player);
        assertTrue(cmds.contains("red"));
        assertTrue(cmds.contains("green"));
        assertTrue(cmds.contains("blue"));
        assertTrue(cmds.contains("yellow"));
    }

    @Test
    public void testPanelSubcommandSuggestions() {
        PlayerMock player = addPlayer();
        List<String> cmds = suggestions("features panel ", player);
        assertTrue(cmds.contains("spawn"));
        assertTrue(cmds.contains("info"));
    }
}
