package io.github.projectunified.craftcommand.bukkit;

import io.github.projectunified.craftcommand.BaseCommand;
import io.github.projectunified.craftcommand.CommandManager;
import org.bukkit.Bukkit;
import org.bukkit.ChatColor;
import org.bukkit.Server;
import org.bukkit.command.Command;
import org.bukkit.command.CommandMap;
import org.bukkit.command.CommandSender;
import org.bukkit.command.SimpleCommandMap;
import org.bukkit.plugin.java.JavaPlugin;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.function.BiConsumer;
import java.util.logging.Level;

/**
 * CommandManager implementation for Bukkit plugins.
 * Handles command registration to the Bukkit CommandMap, syncing commands, and default Bukkit type resolvers (Player, World, Location, etc.).
 */
public class BukkitCommandManager extends CommandManager<CommandSender> {
    private static volatile MethodHandle commandMapGetter;
    private static volatile MethodHandle knownCommandsGetter;
    private static volatile MethodHandle syncCommandsHandle;
    private static volatile boolean syncCommandsAttempted;

    private final JavaPlugin plugin;
    private final Map<String, Command> registered = new HashMap<>();

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

    private static CommandMap getCommandMap() {
        MethodHandle getter = commandMapGetter;
        if (getter == null) {
            Server server = Bukkit.getServer();
            if (server == null) {
                throw new IllegalStateException("Bukkit server is not initialized yet");
            }
            try {
                Method method = server.getClass().getMethod("getCommandMap");
                getter = MethodHandles.lookup().unreflect(method);
                commandMapGetter = getter;
            } catch (ReflectiveOperationException e) {
                throw new IllegalStateException("Unable to retrieve CommandMap from Bukkit server", e);
            }
        }
        try {
            return (CommandMap) getter.invoke(Bukkit.getServer());
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to invoke getCommandMap on Bukkit server", e);
        }
    }

    @SuppressWarnings("unchecked")
    private static Map<String, Command> getKnownCommands(CommandMap commandMap) {
        MethodHandle getter = knownCommandsGetter;
        if (getter == null) {
            try {
                Method method = SimpleCommandMap.class.getDeclaredMethod("getKnownCommands");
                getter = MethodHandles.lookup().unreflect(method);
            } catch (ReflectiveOperationException e) {
                try {
                    Field field = SimpleCommandMap.class.getDeclaredField("knownCommands");
                    field.setAccessible(true);
                    getter = MethodHandles.lookup().unreflectGetter(field);
                } catch (ReflectiveOperationException ex) {
                    throw new IllegalStateException("Unable to access knownCommands in SimpleCommandMap", ex);
                }
            }
            knownCommandsGetter = getter;
        }
        try {
            return (Map<String, Command>) getter.invoke(commandMap);
        } catch (Throwable e) {
            throw new IllegalStateException("Failed to retrieve knownCommands from CommandMap", e);
        }
    }

    private static void registerCommandToCommandMap(String label, Command command) {
        getCommandMap().register(label, command);
    }

    private static void unregisterFromKnownCommands(Command command) {
        CommandMap map = getCommandMap();
        Map<String, Command> knownCommands = getKnownCommands(map);
        knownCommands.values().removeIf(command::equals);
        command.unregister(map);
    }

    /**
     * Synchronizes commands to the client (calls Server#syncCommands if available).
     */
    public void syncCommand() {
        if (!syncCommandsAttempted) {
            Server server = Bukkit.getServer();
            if (server != null) {
                try {
                    Method method = server.getClass().getDeclaredMethod("syncCommands");
                    method.setAccessible(true);
                    syncCommandsHandle = MethodHandles.lookup().unreflect(method);
                } catch (ReflectiveOperationException ignored) {
                    // syncCommands not supported on this platform/version
                }
            }
            syncCommandsAttempted = true;
        }
        if (syncCommandsHandle != null) {
            try {
                syncCommandsHandle.invoke(Bukkit.getServer());
            } catch (Throwable t) {
                Bukkit.getLogger().log(Level.WARNING, "Error when syncing commands", t);
            }
        }
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

    /**
     * Gets all registered Bukkit commands as an unmodifiable map.
     *
     * @return an unmodifiable map of command labels to Command objects
     */
    public Map<String, Command> getRegisteredCommands() {
        return Collections.unmodifiableMap(registered);
    }

    @Override
    public void register(Object commandInstance) {
        if (commandInstance instanceof Command) {
            register((Command) commandInstance);
        } else {
            try {
                Command command = instantiateWrapper(commandInstance, "$BukkitCommand", Command.class);
                if (command instanceof BaseCommand) {
                    registerWrapper(commandInstance, (BaseCommand) command);
                }
                register(command);
            } catch (Throwable e) {
                throw new IllegalArgumentException("Failed to register Bukkit command: " + commandInstance.getClass().getName(), e);
            }
        }
    }
}
