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
                @SuppressWarnings("unchecked")
                Class<Enum> enumType = (Class<Enum>) type;
                return new io.github.projectunified.craftcommand.ArgumentResolver<Object, Object>() {
                    @Override
                    public Object resolve(Object sender, String[] current, String[] context) throws Exception {
                        if (current.length == 0) return null;
                        return Enum.valueOf(enumType, current[0]);
                    }

                    @Override
                    public java.util.List<String> suggest(Object sender, String[] current, String[] context) {
                        String prefix = current.length > 0 ? current[0].toUpperCase() : "";
                        java.util.List<String> result = new java.util.ArrayList<>();
                        for (Enum e : enumType.getEnumConstants()) {
                            if (e.name().startsWith(prefix)) {
                                result.add(e.name());
                            }
                        }
                        return result;
                    }
                };
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
