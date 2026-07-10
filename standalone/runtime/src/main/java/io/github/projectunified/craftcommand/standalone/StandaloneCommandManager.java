package io.github.projectunified.craftcommand.standalone;

import io.github.projectunified.craftcommand.CommandInfo;
import io.github.projectunified.craftcommand.CommandManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;

/**
 * CommandManager implementation for standalone applications.
 * Handles registration and retrieval of standalone commands.
 */
public class StandaloneCommandManager extends CommandManager<Object> {
    private final Map<String, StandaloneCommand> commands = new HashMap<>();
    private final Map<Object, StandaloneCommand> wrappers = new HashMap<>();

    /**
     * Constructs a StandaloneCommandManager with a custom error handler.
     *
     * @param errorHandler the error handler
     */
    public StandaloneCommandManager(BiConsumer<Object, Exception> errorHandler) {
        super(errorHandler);
    }

    /**
     * Constructs a StandaloneCommandManager with a default error handler that propagates exceptions.
     */
    public StandaloneCommandManager() {
        super((sender, exception) -> {
            if (exception instanceof RuntimeException) {
                throw (RuntimeException) exception;
            }
            throw new RuntimeException(exception);
        });
    }

    @Override
    public void register(Object commandInstance) {
        try {
            Class<?> commandClass = commandInstance.getClass();
            StandaloneCommand command = (StandaloneCommand) instantiate(commandClass, commandInstance);
            wrappers.put(commandInstance, command);
            registerCommand(command);
        } catch (Throwable e) {
            throw new IllegalArgumentException("Failed to register standalone command: " + commandInstance.getClass().getName(), e);
        }
    }

    @Override
    public List<CommandInfo> getCommandInfo(Object commandInstance) {
        StandaloneCommand wrapper = wrappers.get(commandInstance);
        if (wrapper != null) {
            return wrapper.getCommandInfo();
        }
        return Collections.emptyList();
    }

    private Object instantiate(Class<?> commandClass, Object instance) throws Throwable {
        Class<?> wrapperClass = Class.forName(commandClass.getName() + "_Standalone");
        java.lang.invoke.MethodHandle handle = java.lang.invoke.MethodHandles.lookup()
                .findConstructor(wrapperClass, java.lang.invoke.MethodType.methodType(void.class, commandClass, CommandManager.class));
        return handle.invoke(instance, this);
    }

    /**
     * Registers a StandaloneCommand instance and its aliases.
     *
     * @param command the standalone command instance
     */
    public void registerCommand(StandaloneCommand command) {
        commands.put(command.getName().toLowerCase(), command);
        for (String alias : command.getAliases()) {
            commands.put(alias.toLowerCase(), command);
        }
    }

    /**
     * Gets a registered command by its label or alias.
     *
     * @param label the command label or alias
     * @return the command instance, or {@code null} if not found
     */
    public StandaloneCommand getCommand(String label) {
        return commands.get(label.toLowerCase());
    }

    /**
     * Gets all registered commands.
     *
     * @return a map of command labels to command instances
     */
    public Map<String, StandaloneCommand> getCommands() {
        return commands;
    }
}
