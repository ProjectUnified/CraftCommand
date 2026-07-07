package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.paper.PaperCommandManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ExamplePlugin extends JavaPlugin {
    private PaperCommandManager commandManager;

    @Override
    public void onEnable() {
        this.commandManager = new PaperCommandManager(this);

        // Register the command
        commandManager.register(new TeleportCommand(commandManager));
        commandManager.register(new BroadcastCommand());

        getLogger().info("ExamplePlugin enabled and Paper commands registered!");
    }
}
