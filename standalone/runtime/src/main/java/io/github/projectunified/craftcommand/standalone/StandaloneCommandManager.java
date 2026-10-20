package io.github.projectunified.craftcommand.standalone;

import io.github.projectunified.craftcommand.CommandManager;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

/**
 * CommandManager implementation for standalone applications.
 * Handles registration and retrieval of standalone commands.
 */
public class StandaloneCommandManager extends CommandManager<Object> {
    private final Map<String, StandaloneCommand> commands = new HashMap<>();

    @Override
    protected void sendMessage(Object sender, String message) {
        System.out.println(message);
    }

    @Override
    public void handleException(Object sender, Exception exception) {
        if (exception instanceof RuntimeException) throw (RuntimeException) exception;
        throw new RuntimeException(exception);
    }

    @Override
    public void register(Object commandInstance) {
        if (commandInstance instanceof StandaloneCommand) {
            registerCommand((StandaloneCommand) commandInstance);
            return;
        }
        try {
            StandaloneCommand command = instantiateWrapper(commandInstance, "$StandaloneCommand", StandaloneCommand.class);
            registerWrapper(commandInstance, command);
            registerCommand(command);
        } catch (Throwable e) {
            throw new IllegalArgumentException("Failed to register standalone command: " + commandInstance.getClass().getName(), e);
        }
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
     * @return an unmodifiable map of command labels to command instances
     */
    public Map<String, StandaloneCommand> getCommands() {
        return Collections.unmodifiableMap(commands);
    }
}
