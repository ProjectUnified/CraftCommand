package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.standalone.StandaloneCommand;
import io.github.projectunified.craftcommand.standalone.StandaloneCommandManager;
import org.junit.jupiter.api.BeforeEach;

import java.util.List;

public abstract class AbstractStandaloneCommandTest {
    protected StandaloneCommandManager manager;
    protected StandaloneCommand cmd;
    protected TestSender sender;

    @BeforeEach
    public void setUp() {
        manager = new StandaloneCommandManager();
        registerCommand();
        cmd = manager.getCommand(getCommandName());
        sender = new TestSender("test");
    }

    protected abstract void registerCommand();

    protected abstract String getCommandName();

    protected boolean execute(String... args) {
        return cmd.execute(sender, args);
    }

    protected List<String> tabComplete(String... args) {
        return cmd.tabComplete(sender, args);
    }
}
