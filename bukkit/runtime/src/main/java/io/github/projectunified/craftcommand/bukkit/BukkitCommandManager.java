package io.github.projectunified.craftcommand.bukkit;

import io.github.projectunified.craftcommand.CommandInfo;
import io.github.projectunified.craftcommand.CommandManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.function.Supplier;
import java.util.logging.Level;

/**
 * CommandManager implementation for Bukkit plugins.
 * Handles command registration to the Bukkit CommandMap, syncing commands, and default Bukkit type resolvers (Player, World, Location, etc.).
 */
public class BukkitCommandManager extends CommandManager<CommandSender> {
    private static final Supplier<CommandMap> COMMAND_MAP_SUPPLIER;
    private static final Supplier<Map<?, ?>> KNOWN_COMMANDS_SUPPLIER;
    private static final Runnable SYNC_COMMANDS_RUNNABLE;

    static {
        Method commandMapMethod;
        try {
            commandMapMethod = Bukkit.getServer().getClass().getMethod("getCommandMap");
        } catch (NoSuchMethodException e) {
            throw new ExceptionInInitializerError(e);
        }

        COMMAND_MAP_SUPPLIER = () -> {
            try {
                return (CommandMap) commandMapMethod.invoke(Bukkit.getServer());
            } catch (ReflectiveOperationException e) {
                throw new ExceptionInInitializerError(e);
            }
        };

        Supplier<Map<?, ?>> knownCommandsSupplier;
        try {
            Method knownCommandsMethod = SimpleCommandMap.class.getDeclaredMethod("getKnownCommands");
            knownCommandsSupplier = () -> {
                try {
                    return (Map<?, ?>) knownCommandsMethod.invoke(COMMAND_MAP_SUPPLIER.get());
                } catch (ReflectiveOperationException e) {
                    throw new ExceptionInInitializerError(e);
                }
            };
        } catch (NoSuchMethodException e) {
            try {
                Field knownCommandsField = SimpleCommandMap.class.getDeclaredField("knownCommands");
                knownCommandsField.setAccessible(true);
                knownCommandsSupplier = () -> {
                    try {
                        return (Map<?, ?>) knownCommandsField.get(COMMAND_MAP_SUPPLIER.get());
                    } catch (ReflectiveOperationException ex) {
                        throw new ExceptionInInitializerError(ex);
                    }
                };
            } catch (ReflectiveOperationException ex) {
                throw new ExceptionInInitializerError(ex);
            }
        }
        KNOWN_COMMANDS_SUPPLIER = knownCommandsSupplier;

        Runnable syncCommandsRunnable;
        try {
            Class<?> craftServer = Bukkit.getServer().getClass();
            Method syncCommandsMethod = craftServer.getDeclaredMethod("syncCommands");
            syncCommandsMethod.setAccessible(true);
            syncCommandsRunnable = () -> {
                try {
                    syncCommandsMethod.invoke(Bukkit.getServer());
                } catch (ReflectiveOperationException e) {
                    Bukkit.getLogger().log(Level.WARNING, "Error when syncing commands", e);
                }
            };
        } catch (Exception e) {
            syncCommandsRunnable = () -> {
            };
        }
        SYNC_COMMANDS_RUNNABLE = syncCommandsRunnable;
    }

    private final JavaPlugin plugin;
    private final Map<String, Command> registered = new HashMap<>();
    private final Map<Object, Command> wrappers = new HashMap<>();

    public BukkitCommandManager(JavaPlugin plugin, BiConsumer<CommandSender, Exception> errorHandler) {
        super(errorHandler);
        this.plugin = plugin;
    }

    /**
     * Constructs a BukkitCommandManager with a default error handler that sends red messages.
     *
     * @param plugin the JavaPlugin instance
     */
    public BukkitCommandManager(JavaPlugin plugin) {
        super((sender, exception) -> sender.sendMessage(ChatColor.RED + exception.getMessage()));
        this.plugin = plugin;
    }

    private static void registerCommandToCommandMap(String label, Command command) {
        COMMAND_MAP_SUPPLIER.get().register(label, command);
    }

    private static void unregisterFromKnownCommands(Command command) {
        Map<?, ?> knownCommands = KNOWN_COMMANDS_SUPPLIER.get();
        knownCommands.values().removeIf(command::equals);
        command.unregister(COMMAND_MAP_SUPPLIER.get());
    }

    /**
     * Synchronizes commands to the client (calls Server#syncCommands if available).
     */
    public void syncCommand() {
        SYNC_COMMANDS_RUNNABLE.run();
    }


    /**
     * Registers a Bukkit Command object to the server's command map.
     *
     * @param command the command object
     */
    public void register(Command command) {
        String name = command.getLabel();
        if (this.registered.containsKey(name)) {
            this.plugin.getLogger().log(Level.WARNING, "Duplicated \"{0}\" command ! Ignored", name);
            return;
        }

        registerCommandToCommandMap(this.plugin.getName(), command);
        this.registered.put(name, command);
    }

    /**
     * Unregisters all commands registered by this command manager.
     */
    public void unregisterAll() {
        this.registered.values().forEach(BukkitCommandManager::unregisterFromKnownCommands);
        this.registered.clear();
    }

    @Override
    public void register(Object commandInstance) {
        if (commandInstance instanceof Command) {
            register((Command) commandInstance);
        } else {
            try {
                Class<?> commandClass = commandInstance.getClass();
                Command command = (Command) instantiate(commandClass, commandInstance);
                wrappers.put(commandInstance, command);
                register(command);
            } catch (Throwable e) {
                throw new IllegalArgumentException("Failed to register Bukkit command: " + commandInstance.getClass().getName(), e);
            }
        }
    }

    @Override
    @SuppressWarnings("unchecked")
    public List<CommandInfo> getCommandInfo(Object commandInstance) {
        Command wrapper = wrappers.get(commandInstance);
        if (wrapper == null) return Collections.emptyList();

        try {
            Method getCommandInfoMethod = wrapper.getClass().getMethod("getCommandInfo");
            Object result = getCommandInfoMethod.invoke(wrapper);
            if (result instanceof List) {
                return (List<CommandInfo>) result;
            }
        } catch (Exception e) {
            // Ignore reflection errors
        }

        return Collections.emptyList();
    }

    private Object instantiate(Class<?> commandClass, Object instance) throws Throwable {
        Class<?> wrapperClass = Class.forName(commandClass.getName() + "_Executor");
        java.lang.invoke.MethodHandle handle = java.lang.invoke.MethodHandles.lookup()
                .findConstructor(wrapperClass, java.lang.invoke.MethodType.methodType(void.class, commandClass, CommandManager.class));
        return handle.invoke(instance, this);
    }
}
