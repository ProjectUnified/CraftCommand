package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.bukkit.BukkitCommandManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ExamplePlugin extends JavaPlugin {
    private BukkitCommandManager commandManager;

    @Override
    public void onEnable() {
        this.commandManager = new BukkitCommandManager(this);

        commandManager.register(new BukkitSuggestCommand());
        commandManager.register(new BukkitResolveCommand());
        commandManager.register(new SenderTypeCommand());
        commandManager.register(new PermissionCommand());
        commandManager.syncCommand();

        getLogger().info("ExamplePlugin enabled and commands registered!");
    }

    @Override
    public void onDisable() {
        this.commandManager.unregisterAll();
    }
}
