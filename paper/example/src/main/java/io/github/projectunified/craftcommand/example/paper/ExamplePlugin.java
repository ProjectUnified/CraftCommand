package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.ArgumentResolver;
import io.github.projectunified.craftcommand.paper.PaperCommandManager;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.Locale;

public class ExamplePlugin extends JavaPlugin {
    private PaperCommandManager commandManager;

    @Override
    public void onEnable() {
        this.commandManager = new PaperCommandManager(this);

        commandManager.registerResolver(BroadcastCommand.BroadcastType.class, (sender, args, current) -> BroadcastCommand.BroadcastType.valueOf(current.toUpperCase(Locale.ROOT)));

        // Register the command
        commandManager.register(new TeleportCommand(commandManager));
        commandManager.register(new BroadcastCommand());

        getLogger().info("ExamplePlugin enabled and Paper commands registered!");
    }
}
