package io.github.projectunified.craftcommand.paper;

import io.github.projectunified.craftcommand.CommandManager;
import io.github.projectunified.craftcommand.ErrorHandler;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.ArrayList;
import java.util.List;

/**
 * CommandManager implementation for Paper plugins using Brigadier.
 */
public class PaperCommandManager extends CommandManager<CommandSourceStack> {
    private final JavaPlugin plugin;
    private final List<PaperCommand> registered = new ArrayList<>();
    private final List<PaperBasicCommand> registeredBasic = new ArrayList<>();

    /**
     * Constructs a PaperCommandManager with a custom error handler.
     *
     * @param plugin       the JavaPlugin instance
     * @param errorHandler the custom error handler
     */
    public PaperCommandManager(JavaPlugin plugin, ErrorHandler<CommandSourceStack> errorHandler) {
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
                net.kyori.adventure.text.Component.text(exception.getMessage(), net.kyori.adventure.text.format.NamedTextColor.RED)
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
            for (PaperBasicCommand command : registeredBasic) {
                event.registrar().register(
                        command.getName(),
                        command.getDescription(),
                        command.getAliases(),
                        command
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
     * Registers a PaperBasicCommand wrapper.
     *
     * @param command the command wrapper
     */
    public void register(PaperBasicCommand command) {
        this.registeredBasic.add(command);
    }

    @Override
    public void register(Object commandInstance) {
        if (commandInstance instanceof PaperCommand) {
            register((PaperCommand) commandInstance);
        } else if (commandInstance instanceof PaperBasicCommand) {
            register((PaperBasicCommand) commandInstance);
        } else {
            try {
                Object wrapper = null;
                Class<?> cmdClass = commandInstance.getClass();

                try {
                    Class<?> wrapperClass = Class.forName(cmdClass.getName() + "_Paper");
                    wrapper = wrapperClass.getConstructor(cmdClass, CommandManager.class)
                            .newInstance(commandInstance, this);
                } catch (ClassNotFoundException ignored) {
                    Class<?> wrapperClass = Class.forName(cmdClass.getName() + "_PaperBasic");
                    wrapper = wrapperClass.getConstructor(cmdClass, CommandManager.class)
                            .newInstance(commandInstance, this);
                }

                if (wrapper instanceof PaperCommand) {
                    if (wrapper instanceof io.github.projectunified.craftcommand.CommandInfoExposer) {
                        registerExposer(commandInstance, (io.github.projectunified.craftcommand.CommandInfoExposer) wrapper);
                    }
                    register((PaperCommand) wrapper);
                } else if (wrapper instanceof PaperBasicCommand) {
                    if (wrapper instanceof io.github.projectunified.craftcommand.CommandInfoExposer) {
                        registerExposer(commandInstance, (io.github.projectunified.craftcommand.CommandInfoExposer) wrapper);
                    }
                    register((PaperBasicCommand) wrapper);
                } else {
                    throw new IllegalArgumentException("Wrapper is neither PaperCommand nor PaperBasicCommand: " + wrapper.getClass());
                }
            } catch (Exception e) {
                throw new IllegalArgumentException("Failed to register Paper command: " + commandInstance.getClass().getName(), e);
            }
        }
    }
}
