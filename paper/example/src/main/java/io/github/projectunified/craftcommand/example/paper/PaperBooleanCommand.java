package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Resolve;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/**
 * Covers a boolean argument on Paper: native Brigadier argument and boolean suggestions.
 */
@Command(value = "pbool", description = "Paper boolean argument")
public class PaperBooleanCommand {
    @Command("flag")
    public void flag(CommandSourceStack sender, boolean enabled) {
        sender.getSender().sendMessage("flag=" + enabled);
    }

    public CustomSender resolveSender(CommandSourceStack source) {
        return new CustomSender(source);
    }

    public boolean resolveFlag(CustomSender sender, boolean flag) {
        return flag;
    }

    @Command("resolved")
    public void resolved(@Resolve("resolveSender") CustomSender sender, @Resolve("resolveFlag") boolean flag) {
        sender.source.getSender().sendMessage("resolved=" + flag);
    }

    public static class CustomSender {
        public final CommandSourceStack source;

        public CustomSender(CommandSourceStack source) {
            this.source = source;
        }
    }
}
