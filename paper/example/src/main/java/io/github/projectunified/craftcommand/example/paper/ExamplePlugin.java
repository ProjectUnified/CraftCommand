package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.paper.PaperCommandManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ExamplePlugin extends JavaPlugin {
    private PaperCommandManager commandManager;

    @Override
    public void onEnable() {
        this.commandManager = new PaperCommandManager(this);

        commandManager.register(new PaperSuggestCommand());
        commandManager.register(new PaperResolveCommand());
        commandManager.register(new PaperSenderTypeCommand());
        commandManager.register(new PaperPermissionCommand());
        commandManager.register(new PaperSenderCommand());

        getLogger().info("ExamplePlugin enabled and Paper commands registered!");
    }
}
