package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Default;
import io.github.projectunified.craftcommand.annotation.Resolve;
import org.bukkit.command.CommandSender;

/**
 * Covers @Resolve with different sender type variants.
 */
@Command(value = "bukkitresolve", description = "Bukkit resolve test commands")
public class BukkitResolveCommand {

    public CustomSender resolveCustomSender(CommandSender sender) {
        return new CustomSender(sender.getName());
    }

    public CustomSender resolveCustomSenderDefault(CommandSender sender) {
        return new CustomSender(sender.getName());
    }

    @Command("named")
    public void namedResolve(CommandSender sender, @Resolve("resolveCustomSender") CustomSender custom) {
        sender.sendMessage("named=" + custom.name);
    }

    @Command("def")
    public void resolveDefault(CommandSender sender, @Default("test") @Resolve("resolveCustomSenderDefault") CustomSender custom) {
        sender.sendMessage("def=" + custom.name);
    }

    public static class CustomSender {
        public final String name;

        public CustomSender(String name) {
            this.name = name;
        }
    }
}
