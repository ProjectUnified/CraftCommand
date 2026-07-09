package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.annotation.*;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.github.projectunified.craftcommand.validation.annotation.Max;
import io.github.projectunified.craftcommand.validation.annotation.Min;
import io.github.projectunified.craftcommand.validation.annotation.ValidateWith;
import io.papermc.paper.command.brigadier.CommandSourceStack;
import org.bukkit.Bukkit;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Command(value = "features", aliases = {"f"}, description = "All CraftCommand features combined for Paper")
@Permission("features.base")
public class AllFeaturesCommand {

    // ── Suggestion Providers ──
    public final List<String> modes = Arrays.asList("normal", "silent", "instant");
    public final List<String> colors = Arrays.asList("red", "green", "blue", "yellow");

    public List<String> getNearPlayers(Player sender, String[] args, String current) {
        List<String> suggestions = new ArrayList<>();
        String lower = current.toLowerCase();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(lower)) {
                suggestions.add(p.getName());
            }
        }
        return suggestions;
    }

    // ── Custom Resolvers ──
    public Point resolvePoint(double x, double y) {
        return new Point(x, y);
    }

    public Point resolvePointWithSender(CommandSourceStack sender, double x, double y) {
        return new Point(x, y);
    }

    public Point resolvePointDefault(double x, @Default("0") double y) {
        return new Point(x, y);
    }

    public Point resolvePointSuggest(@Suggest("modes") String mode, double x) {
        return new Point(x, mode.hashCode() % 100);
    }

    public Point resolvePointNamed(@Name("ax") double x, @Name("ay") double y) {
        return new Point(x, y);
    }

    public int resolveClamped(@Min(0) @Max(100) int value) {
        return value;
    }

    public String resolveGreedy(@Greedy String text) {
        return text;
    }

    // ── Custom Validators ──
    public void validateEven(int value) {
        if (value % 2 != 0) {
            throw new IllegalArgumentException("Value must be even!");
        }
    }

    public void validatePoint(Point pt) {
        if (pt.x < 0 || pt.y < 0) {
            throw new IllegalArgumentException("Point coordinates must be non-negative!");
        }
    }

    // ── Default Action ──
    @Default
    public void execute(CommandSourceStack sender, @Default Player target) {
        if (target == null) {
            sender.getSender().sendMessage("Default: no target");
        } else {
            sender.getSender().sendMessage("Default: " + target.getName());
        }
    }

    // ── Single-Param Annotation Stacking ──
    @Command("hello")
    public void hello(CommandSourceStack sender, @Name("greeting") String greeting) {
        sender.getSender().sendMessage("Hello, " + greeting + "!");
    }

    @Command("add")
    public void add(CommandSourceStack sender, @Name("a") @Min(0) @Max(100) int x, @Name("b") int y) {
        sender.getSender().sendMessage("result=" + (x + y));
    }

    @Command("range")
    public void range(CommandSourceStack sender, @Min(0) @Max(100) @Default("50") int value) {
        sender.getSender().sendMessage("range=" + value);
    }

    @Command("validated")
    public void validated(CommandSourceStack sender, @Min(0) @Max(100) @ValidateWith("validateEven") @Default("50") int value) {
        sender.getSender().sendMessage("validated=" + value);
    }

    @Command("echo")
    public void echo(CommandSourceStack sender, @Greedy String text) {
        sender.getSender().sendMessage("echo=" + text);
    }

    @Command("namedecho")
    public void namedEcho(CommandSourceStack sender, @Name("message") @Greedy String text) {
        sender.getSender().sendMessage("namedecho=" + text);
    }

    @Command("suggestecho")
    public void suggestEcho(CommandSourceStack sender, @Greedy @Suggest("colors") String text) {
        sender.getSender().sendMessage("suggestecho=" + text);
    }

    @Command("fullgreedy")
    public void fullGreedy(CommandSourceStack sender, @Name("text") @Greedy @Suggest("colors") @Default("red") String text) {
        sender.getSender().sendMessage("fullgreedy=" + text);
    }

    @Command("mode")
    public void mode(CommandSourceStack sender, @Suggest("modes") String mode) {
        sender.getSender().sendMessage("mode=" + mode);
    }

    @Command("namedmode")
    public void namedMode(CommandSourceStack sender, @Name("mode") @Suggest("modes") String mode) {
        sender.getSender().sendMessage("namedmode=" + mode);
    }

    @Command("defaultmode")
    public void defaultMode(CommandSourceStack sender, @Default("normal") @Suggest("modes") @Name("mode") String mode) {
        sender.getSender().sendMessage("defaultmode=" + mode);
    }

    // ── Resolver Commands ──
    @Command("resolve")
    public void resolve(CommandSourceStack sender, @Resolve("resolvePoint") Point pt) {
        sender.getSender().sendMessage("resolve=" + pt.x + "," + pt.y);
    }

    @Command("resolveDefault")
    public void resolveDefault(CommandSourceStack sender, @Resolve("resolvePointDefault") Point pt) {
        sender.getSender().sendMessage("resolveDefault=" + pt.x + "," + pt.y);
    }

    @Command("resolveSuggest")
    public void resolveSuggest(CommandSourceStack sender, @Resolve("resolvePointSuggest") Point pt) {
        sender.getSender().sendMessage("resolveSuggest=" + pt.x + "," + pt.y);
    }

    @Command("resolveNamed")
    public void resolveNamed(CommandSourceStack sender, @Resolve("resolvePointNamed") Point pt) {
        sender.getSender().sendMessage("resolveNamed=" + pt.x + "," + pt.y);
    }

    @Command("resolveClamped")
    public void resolveClamped(CommandSourceStack sender, @Resolve("resolveClamped") int value) {
        sender.getSender().sendMessage("resolveClamped=" + value);
    }

    @Command("resolveGreedy")
    public void resolveGreedy(CommandSourceStack sender, @Resolve("resolveGreedy") String text) {
        sender.getSender().sendMessage("resolveGreedy=" + text);
    }

    @Command("sameSender")
    public void sameSender(CommandSourceStack sender, @Resolve("resolvePointWithSender") Point pt) {
        sender.getSender().sendMessage("sameSender=" + pt.x + "," + pt.y);
    }

    // ── Multi-Param Cross-Parameter Interactions ──
    @Command("resolveAndSuggest")
    public void resolveAndSuggest(CommandSourceStack sender, @Resolve("resolvePoint") Point pt, @Suggest("modes") String mode) {
        sender.getSender().sendMessage("resolveAndSuggest=" + pt.x + "," + pt.y + "," + mode);
    }

    @Command("resolveAndDefault")
    public void resolveAndDefault(CommandSourceStack sender, @Resolve("resolvePoint") Point pt, @Default("normal") String mode) {
        sender.getSender().sendMessage("resolveAndDefault=" + pt.x + "," + pt.y + "," + mode);
    }

    @Command("resolveAndValidate")
    public void resolveAndValidate(CommandSourceStack sender, @Resolve("resolvePoint") Point pt, @Min(0) @Max(100) @Default("50") int level) {
        sender.getSender().sendMessage("resolveAndValidate=" + pt.x + "," + pt.y + "," + level);
    }

    @Command("suggestAndDefault")
    public void suggestAndDefault(CommandSourceStack sender, @Suggest("modes") String mode, @Default("50") int count) {
        sender.getSender().sendMessage("suggestAndDefault=" + mode + "," + count);
    }

    @Command("defaultAndGreedy")
    public void defaultAndGreedy(CommandSourceStack sender, @Default("hello") String greeting, @Greedy String text) {
        sender.getSender().sendMessage("defaultAndGreedy=" + greeting + "," + text);
    }

    @Command("tripleInteract")
    public void tripleInteract(CommandSourceStack sender, @Resolve("resolvePoint") Point pt, @Suggest("colors") @Default("red") String color, @Min(0) @Max(100) @Default("50") int level) {
        sender.getSender().sendMessage("tripleInteract=" + pt.x + "," + pt.y + "," + color + "," + level);
    }

    // ── Permission Variations ──
    @Command("admin")
    @Permission("features.admin")
    public void admin(CommandSourceStack sender) {
        sender.getSender().sendMessage("admin panel");
    }

    @Command("secret")
    @Permission(value = "features.secret", message = "Access denied to secret area!")
    public void secret(CommandSourceStack sender) {
        sender.getSender().sendMessage("secret area");
    }

    // ── Support Types ──
    public static class Point {
        public final double x;
        public final double y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    // ── Nested Subcommand Class ──
    @Command("panel")
    @Permission("features.panel")
    public class PanelCommands {
        @Default
        public void execute(CommandSourceStack sender) {
            sender.getSender().sendMessage("panel open");
        }

        @Command("spawn")
        public void spawn(CommandSourceStack sender, String worldName) {
            sender.getSender().sendMessage("spawn=" + worldName);
        }

        @Command("info")
        public void info(Player sender) {
            sender.sendMessage("info=" + sender.getName());
        }
    }
}
