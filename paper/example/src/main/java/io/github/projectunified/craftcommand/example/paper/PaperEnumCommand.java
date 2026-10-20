package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Default;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/**
 * Covers native enum arguments and suggestions on Paper.
 */
@Command("penum")
public class PaperEnumCommand {
    @Command("mode")
    public void mode(CommandSourceStack sender, Mode mode) {
        sender.getSender().sendMessage("mode=" + mode.name());
    }

    @Command("optional")
    public void optional(CommandSourceStack sender, @Default("FAST") Mode mode) {
        sender.getSender().sendMessage("optional=" + mode.name());
    }

    public enum Mode {
        FAST, SLOW
    }
}
