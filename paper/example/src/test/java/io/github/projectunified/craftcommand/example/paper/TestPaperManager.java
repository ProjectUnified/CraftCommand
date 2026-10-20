package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.ArgumentResolver;
import io.github.projectunified.craftcommand.CommandInfo;
import io.github.projectunified.craftcommand.CommandManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import net.kyori.adventure.text.Component;

import java.util.List;

class TestPaperManager extends CommandManager<Object> {
    TestPaperManager() {
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
    protected void sendMessage(Object sender, String message) {
        if (sender instanceof CommandSourceStack) {
            ((CommandSourceStack) sender).getSender().sendMessage(Component.text(message));
        }
    }

    @Override
    public void handleException(Object sender, Exception exception) {
        if (exception instanceof RuntimeException) throw (RuntimeException) exception;
        throw new RuntimeException(exception);
    }

    @Override
    public void register(Object command) {
    }

    @Override
    public List<CommandInfo> getCommandInfo(Object commandInstance) {
        return List.of();
    }
}
