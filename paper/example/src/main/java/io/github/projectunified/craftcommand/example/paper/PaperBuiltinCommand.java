package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.Command;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.GameMode;

import java.util.UUID;

/**
 * Covers Paper's native argument types.
 */
@Command("pbuiltin")
public class PaperBuiltinCommand {
    @Command("gamemode")
    public void gameMode(CommandSourceStack sender, GameMode mode) {
        sender.getSender().sendMessage("gamemode=" + mode.name());
    }

    @Command("uuid")
    public void uuid(CommandSourceStack sender, UUID id) {
        sender.getSender().sendMessage("uuid=" + id);
    }

    @Command("enum")
    public void enumParam(CommandSourceStack sender, Mode mode) {
        sender.getSender().sendMessage("enum=" + mode.name());
    }

    public enum Mode {
        FAST, SLOW
    }
}
