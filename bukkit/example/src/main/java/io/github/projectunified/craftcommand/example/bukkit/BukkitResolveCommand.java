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
        return new CustomSender(sender);
    }

    public CustomString resolveString(String message) {
        return new CustomString(message);
    }

    public CustomString resolveStringWithSender(CustomSender sender, String message) {
        return new CustomString(message + ",name=" + sender.name);
    }

    @Command("named")
    public void namedResolve(@Resolve("resolveCustomSender") CustomSender sender) {
        sender.sendMessage("named=" + sender.name);
    }

    @Command("string")
    public void stringResolve(@Resolve("resolveCustomSender") CustomSender sender, @Resolve("resolveString") CustomString string) {
        sender.sendMessage("string=" + string.message);
    }

    @Command("stringWithDefault")
    public void stringResolveWithDefault(@Resolve("resolveCustomSender") CustomSender sender, @Resolve("resolveStringWithSender") CustomString string) {
        sender.sendMessage("stringWithDefault=" + string.message);
    }

    @Command("def")
    public void defaultResolve(@Resolve("resolveCustomSender") CustomSender sender, @Default("def") @Resolve("resolveString") CustomString string) {
        sender.sendMessage("def=" + string.message);
    }

    public static class CustomSender {
        public final String name;
        private final CommandSender sender;

        public CustomSender(CommandSender sender) {
            this.sender = sender;
            this.name = sender.getName();
        }

        public void sendMessage(String message) {
            sender.sendMessage(message);
        }
    }

    public static class CustomString {
        private final String message;

        public CustomString(String message) {
            this.message = message;
        }

        public String getMessage() {
            return "message=" + message;
        }
    }
}
