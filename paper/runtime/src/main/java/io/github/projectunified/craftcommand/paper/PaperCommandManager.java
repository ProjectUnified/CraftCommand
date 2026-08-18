package io.github.projectunified.craftcommand.paper;

import io.github.projectunified.craftcommand.CommandManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import net.kyori.adventure.text.Component;
import net.kyori.adventure.text.format.NamedTextColor;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.BiConsumer;

/**
 * CommandManager implementation for Paper plugins using Brigadier.
 */
public class PaperCommandManager extends CommandManager<CommandSourceStack> {
    private final JavaPlugin plugin;
    private final List<PaperCommand> registered = new ArrayList<>();

    /**
     * Constructs a PaperCommandManager with a custom error handler.
     *
     * @param plugin       the JavaPlugin instance
     * @param errorHandler the custom error handler
     */
    public PaperCommandManager(JavaPlugin plugin, BiConsumer<CommandSourceStack, Exception> errorHandler) {
        super(errorHandler);
        this.plugin = plugin;

        registerLifecycleListener();
    }

    /**
     * Constructs a PaperCommandManager with a default error handler that sends red messages.
     *
     * @param plugin the JavaPlugin instance
     */
    public PaperCommandManager(JavaPlugin plugin) {
        super((source, exception) -> source.getSender().sendMessage(
                Component.text(exception.getMessage(), NamedTextColor.RED)
        ));
        this.plugin = plugin;

        registerLifecycleListener();
    }

    private void registerLifecycleListener() {
        this.plugin.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, event -> {
            for (PaperCommand command : registered) {
                event.registrar().register(
                        command.getCommandNode(),
                        command.getDescription(),
                        command.getAliases()
                );
            }
        });
    }

    /**
     * Registers a PaperCommand wrapper.
     *
     * @param command the command wrapper
     */
    public void register(PaperCommand command) {
        this.registered.add(command);
    }

    /**
     * Gets all registered Paper commands as an unmodifiable list.
     *
     * @return an unmodifiable list of PaperCommand instances
     */
    public List<PaperCommand> getRegisteredCommands() {
        return Collections.unmodifiableList(registered);
    }

    @Override
    public void register(Object commandInstance) {
        if (commandInstance instanceof PaperCommand) {
            register((PaperCommand) commandInstance);
        } else {
            try {
                PaperCommand paperCommand = instantiateWrapper(commandInstance, "$PaperCommand", PaperCommand.class);
                registerWrapper(commandInstance, paperCommand);
                register(paperCommand);
            } catch (Throwable e) {
                throw new IllegalArgumentException("Failed to register Paper command: " + commandInstance.getClass().getName(), e);
            }
        }
    }
}
