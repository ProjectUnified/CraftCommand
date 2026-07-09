package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Default;
import io.github.projectunified.craftcommand.annotation.Resolve;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.command.CommandSender;

/**
 * Covers @Resolve with different sender type variants.
 * Mirror of Bukkit BukkitResolveCommand.
 */
@Command(value = "presolve", description = "Paper resolve test commands")
public class PaperResolveCommand {

    public CustomSender resolveCustomSender(CommandSourceStack stack) {
        return new CustomSender(stack.getSender().getName());
    }

    public CustomSender resolveCustomSenderDefault(CommandSourceStack stack) {
        return new CustomSender(stack.getSender().getName());
    }

    @Command("named")
    public void namedResolve(CommandSourceStack sender, @Resolve("resolveCustomSender") CustomSender custom) {
        sender.getSender().sendMessage("named=" + custom.name);
    }

    @Command("def")
    public void resolveDefault(CommandSourceStack sender, @Default("test") @Resolve("resolveCustomSenderDefault") CustomSender custom) {
        sender.getSender().sendMessage("def=" + custom.name);
    }

    public static class CustomSender {
        public final String name;
        public CustomSender(String name) { this.name = name; }
    }
}
