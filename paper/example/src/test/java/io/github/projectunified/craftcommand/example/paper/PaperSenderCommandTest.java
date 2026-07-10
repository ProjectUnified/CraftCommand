package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.CommandInfo;
import io.github.projectunified.craftcommand.CommandManager;
import io.github.projectunified.craftcommand.paper.PaperCommand;
import org.junit.jupiter.api.Test;

import java.lang.reflect.Constructor;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

public class PaperSenderCommandTest extends AbstractPaperCommandTest {

    private Object createWrapper() throws Exception {
        PaperSenderCommand instance = new PaperSenderCommand();
        Constructor<?> ctor = PaperSenderCommand_Paper.class.getDeclaredConstructor(PaperSenderCommand.class, CommandManager.class);
        ctor.setAccessible(true);
        return ctor.newInstance(instance, null);
    }

    @Test
    public void testCommandInfo() throws Exception {
        PaperCommand wrapper = (PaperCommand) createWrapper();
        List<CommandInfo> infoList = wrapper.getCommandInfo();
        assertNotNull(infoList);
        assertFalse(infoList.isEmpty());
    }

    @Test
    public void testCommandInfoDescription() throws Exception {
        Object wrapper = createWrapper();
        assertEquals("Paper sender test commands", wrapper.getClass().getMethod("getDescription").invoke(wrapper));
    }

    @Test
    public void testCommandInfoSubcommands() throws Exception {
        PaperCommand wrapper = (PaperCommand) createWrapper();
        List<CommandInfo> infoList = wrapper.getCommandInfo();
        assertTrue(infoList.stream().anyMatch(info -> info.getPath().contains("css")));
        assertTrue(infoList.stream().anyMatch(info -> info.getPath().contains("cs")));
    }
}
