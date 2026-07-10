package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Default;
import io.github.projectunified.craftcommand.annotation.Resolve;
import io.papermc.paper.command.brigadier.CommandSourceStack;

/**
 * Covers @Resolve with different sender type variants for Paper.
 */
@Command(value = "paperresolve", description = "Paper resolve test commands")
public class PaperResolveCommand {

    public CustomSender resolveCustomSender(CommandSourceStack sender) {
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
        private final CommandSourceStack sender;

        public CustomSender(CommandSourceStack sender) {
            this.sender = sender;
            this.name = sender.getSender().getName();
        }

        public void sendMessage(String message) {
            sender.getSender().sendMessage(message);
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
