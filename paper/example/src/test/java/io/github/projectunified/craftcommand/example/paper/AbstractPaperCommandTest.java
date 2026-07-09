package io.github.projectunified.craftcommand.example.paper;

import com.mojang.brigadier.CommandDispatcher;
import io.github.projectunified.craftcommand.CommandManager;
import io.github.projectunified.craftcommand.paper.PaperCommand;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;
import org.mockbukkit.mockbukkit.entity.PlayerMock;

import java.lang.reflect.Constructor;

public abstract class AbstractPaperCommandTest {
    protected ServerMock server;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    protected CommandDispatcher<CommandSourceStack> register(Class<?> commandClass) {
        try {
            TestPaperManager manager = new TestPaperManager();
            Class<?> wrapperClass = Class.forName(commandClass.getName() + "_Paper");
            Constructor<?> ctor = wrapperClass.getDeclaredConstructor(commandClass, CommandManager.class);
            PaperCommand wrapper = (PaperCommand) ctor.newInstance(commandClass.getDeclaredConstructor().newInstance(), manager);
            CommandDispatcher<CommandSourceStack> dispatcher = new CommandDispatcher<>();
            dispatcher.getRoot().addChild(wrapper.getCommandNode());
            return dispatcher;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    protected CommandSourceStack source(PlayerMock player) {
        CommandSourceStack s = org.mockito.Mockito.mock(CommandSourceStack.class);
        org.mockito.Mockito.when(s.getSender()).thenReturn(player);
        return s;
    }
}
