package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.ArgumentResolver;
import io.github.projectunified.craftcommand.CommandInfo;
import io.github.projectunified.craftcommand.CommandManager;

import java.util.List;

class TestPaperManager extends CommandManager<Object> {
    TestPaperManager() {
        super((sender, exception) -> {
            if (exception instanceof RuntimeException) throw (RuntimeException) exception;
            throw new RuntimeException(exception);
        });
        registerProvider(type -> {
            if (type != PaperBooleanCommand.CustomValue.class) return null;
            return new ArgumentResolver<Object, Object>() {
                @Override
                public Object resolve(Object sender, String[] current, String[] context) {
                    return new PaperBooleanCommand.CustomValue(current[0], context.length);
                }
            };
        });
    }

    @Override
    public void register(Object command) {
    }

    @Override
    public List<CommandInfo> getCommandInfo(Object commandInstance) {
        return List.of();
    }
}
