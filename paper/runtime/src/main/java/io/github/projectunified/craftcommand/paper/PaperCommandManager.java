package io.github.projectunified.craftcommand.paper;

import io.github.projectunified.craftcommand.CommandInfo;
import io.github.projectunified.craftcommand.CommandManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.*;
import java.util.function.BiConsumer;

/**
 * CommandManager implementation for Paper plugins using Brigadier.
 */
public class PaperCommandManager extends CommandManager<CommandSourceStack> {
    private final JavaPlugin plugin;
    private final List<PaperCommand> registered = new ArrayList<>();
    private final Map<Object, PaperCommand> wrappers = new HashMap<>();

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

    @Override
    public void register(Object commandInstance) {
        if (commandInstance instanceof PaperCommand) {
            register((PaperCommand) commandInstance);
        } else {
            try {
                Object wrapper = instantiate(commandInstance.getClass(), commandInstance);

                if (wrapper instanceof PaperCommand) {
                    PaperCommand paperCommand = (PaperCommand) wrapper;
                    wrappers.put(commandInstance, paperCommand);
                    register(paperCommand);
                } else {
                    throw new IllegalArgumentException("Wrapper is not PaperCommand: " + wrapper.getClass());
                }
            } catch (Throwable e) {
                throw new IllegalArgumentException("Failed to register Paper command: " + commandInstance.getClass().getName(), e);
            }
        }
    }

    @Override
    public List<CommandInfo> getCommandInfo(Object commandInstance) {
        PaperCommand wrapper = wrappers.get(commandInstance);
        if (wrapper != null) {
            return wrapper.getCommandInfo();
        }
        return Collections.emptyList();
    }

    private Object instantiate(Class<?> commandClass, Object instance) throws Throwable {
        Class<?> wrapperClass = Class.forName(commandClass.getName() + "_Paper");
        java.lang.invoke.MethodHandle handle = java.lang.invoke.MethodHandles.lookup()
                .findConstructor(wrapperClass, java.lang.invoke.MethodType.methodType(void.class, commandClass, CommandManager.class));
        return handle.invoke(instance, this);
    }
}
