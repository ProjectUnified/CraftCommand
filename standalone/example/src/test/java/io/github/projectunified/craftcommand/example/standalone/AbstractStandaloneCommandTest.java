package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.standalone.StandaloneCommand;
import io.github.projectunified.craftcommand.standalone.StandaloneCommandManager;
import org.junit.jupiter.api.BeforeEach;

public abstract class AbstractStandaloneCommandTest {
    protected StandaloneCommandManager manager;
    protected StandaloneCommand cmd;
    protected TestSender sender;

    @BeforeEach
    public void setUp() {
        manager = new StandaloneCommandManager();
        registerEnumProvider(manager);
        registerCommand();
        cmd = manager.getCommand(getCommandName());
        sender = new TestSender("test");
    }

    protected void registerEnumProvider(StandaloneCommandManager manager) {
        manager.registerProvider(type -> {
            if (type.isEnum()) {
                return (sender, args, current) -> Enum.valueOf((Class<Enum>) type, current.toUpperCase());
            }
            return null;
        });
    }

    protected abstract void registerCommand();

    protected abstract String getCommandName();

    protected boolean execute(String... args) {
        return cmd.execute(sender, args);
    }

    protected java.util.List<String> tabComplete(String... args) {
        return cmd.tabComplete(sender, args);
    }
}
