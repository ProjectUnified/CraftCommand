package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.annotation.*;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.github.projectunified.craftcommand.validation.annotation.Max;
import io.github.projectunified.craftcommand.validation.annotation.Min;
import io.github.projectunified.craftcommand.validation.annotation.ValidateWith;
import org.bukkit.Bukkit;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Command(value = "features", aliases = {"f"}, description = "All CraftCommand features combined")
@Permission("features.base")
public class AllFeaturesCommand {

    // ── Suggestion Providers ──
    public final List<String> modes = Arrays.asList("normal", "silent", "instant");
    public final List<String> colors = Arrays.asList("red", "green", "blue", "yellow");

    public java.util.Collection<String> getNearPlayers(Player sender, String[] current) {
        List<String> suggestions = new ArrayList<>();
        String prefix = current.length > 0 ? current[0].toLowerCase() : "";
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getName().toLowerCase().startsWith(prefix)) {
                suggestions.add(p.getName());
            }
        }
        return suggestions;
    }

    // ── Custom Resolvers ──
    public Point resolvePoint(double x, double y) {
        return new Point(x, y);
    }

    public Point resolvePointWithSender(CommandSender sender, double x, double y) {
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
    public void execute(CommandSender sender, @Default Player target) {
        if (target == null) {
            sender.sendMessage("Default: no target");
        } else {
            sender.sendMessage("Default: " + target.getName());
        }
    }

    // ── Single-Param Annotation Stacking ──
    @Command("hello")
    public void hello(CommandSender sender, @Name("greeting") String greeting) {
        sender.sendMessage("Hello, " + greeting + "!");
    }

    @Command("add")
    public void add(CommandSender sender, @Name("a") @Min(0) @Max(100) int x, @Name("b") int y) {
        sender.sendMessage("result=" + (x + y));
    }

    @Command("range")
    public void range(CommandSender sender, @Min(0) @Max(100) @Default("50") int value) {
        sender.sendMessage("range=" + value);
    }

    @Command("validated")
    public void validated(CommandSender sender, @Min(0) @Max(100) @ValidateWith("validateEven") @Default("50") int value) {
        sender.sendMessage("validated=" + value);
    }

    @Command("echo")
    public void echo(CommandSender sender, @Greedy String text) {
        sender.sendMessage("echo=" + text);
    }

    @Command("namedecho")
    public void namedEcho(CommandSender sender, @Name("message") @Greedy String text) {
        sender.sendMessage("namedecho=" + text);
    }

    @Command("suggestecho")
    public void suggestEcho(CommandSender sender, @Greedy @Suggest("colors") String text) {
        sender.sendMessage("suggestecho=" + text);
    }

    @Command("fullgreedy")
    public void fullGreedy(CommandSender sender, @Name("text") @Greedy @Suggest("colors") @Default("red") String text) {
        sender.sendMessage("fullgreedy=" + text);
    }

    @Command("mode")
    public void mode(CommandSender sender, @Suggest("modes") String mode) {
        sender.sendMessage("mode=" + mode);
    }

    @Command("namedmode")
    public void namedMode(CommandSender sender, @Name("mode") @Suggest("modes") String mode) {
        sender.sendMessage("namedmode=" + mode);
    }

    @Command("defaultmode")
    public void defaultMode(CommandSender sender, @Default("normal") @Suggest("modes") @Name("mode") String mode) {
        sender.sendMessage("defaultmode=" + mode);
    }

    // ── Resolver Commands ──
    @Command("resolve")
    public void resolve(CommandSender sender, @Resolve("resolvePoint") Point pt) {
        sender.sendMessage("resolve=" + pt.x + "," + pt.y);
    }

    @Command("resolveDefault")
    public void resolveDefault(CommandSender sender, @Resolve("resolvePointDefault") Point pt) {
        sender.sendMessage("resolveDefault=" + pt.x + "," + pt.y);
    }

    @Command("resolveSuggest")
    public void resolveSuggest(CommandSender sender, @Resolve("resolvePointSuggest") Point pt) {
        sender.sendMessage("resolveSuggest=" + pt.x + "," + pt.y);
    }

    @Command("resolveNamed")
    public void resolveNamed(CommandSender sender, @Resolve("resolvePointNamed") Point pt) {
        sender.sendMessage("resolveNamed=" + pt.x + "," + pt.y);
    }

    @Command("resolveClamped")
    public void resolveClamped(CommandSender sender, @Resolve("resolveClamped") int value) {
        sender.sendMessage("resolveClamped=" + value);
    }

    @Command("resolveGreedy")
    public void resolveGreedy(CommandSender sender, @Resolve("resolveGreedy") String text) {
        sender.sendMessage("resolveGreedy=" + text);
    }

    @Command("sameSender")
    public void sameSender(CommandSender sender, @Resolve("resolvePointWithSender") Point pt) {
        sender.sendMessage("sameSender=" + pt.x + "," + pt.y);
    }

    // ── Multi-Param Cross-Parameter Interactions ──
    @Command("resolveAndSuggest")
    public void resolveAndSuggest(CommandSender sender, @Resolve("resolvePoint") Point pt, @Suggest("modes") String mode) {
        sender.sendMessage("resolveAndSuggest=" + pt.x + "," + pt.y + "," + mode);
    }

    @Command("resolveAndDefault")
    public void resolveAndDefault(CommandSender sender, @Resolve("resolvePoint") Point pt, @Default("normal") String mode) {
        sender.sendMessage("resolveAndDefault=" + pt.x + "," + pt.y + "," + mode);
    }

    @Command("resolveAndValidate")
    public void resolveAndValidate(CommandSender sender, @Resolve("resolvePoint") Point pt, @Min(0) @Max(100) @Default("50") int level) {
        sender.sendMessage("resolveAndValidate=" + pt.x + "," + pt.y + "," + level);
    }

    @Command("suggestAndDefault")
    public void suggestAndDefault(CommandSender sender, @Suggest("modes") String mode, @Default("50") int count) {
        sender.sendMessage("suggestAndDefault=" + mode + "," + count);
    }

    @Command("defaultAndGreedy")
    public void defaultAndGreedy(CommandSender sender, @Default("hello") String greeting, @Greedy String text) {
        sender.sendMessage("defaultAndGreedy=" + greeting + "," + text);
    }

    @Command("tripleInteract")
    public void tripleInteract(CommandSender sender, @Resolve("resolvePoint") Point pt, @Suggest("colors") @Default("red") String color, @Min(0) @Max(100) @Default("50") int level) {
        sender.sendMessage("tripleInteract=" + pt.x + "," + pt.y + "," + color + "," + level);
    }

    // ── Permission Variations ──
    @Command("admin")
    @Permission("features.admin")
    public void admin(CommandSender sender) {
        sender.sendMessage("admin panel");
    }

    @Command("secret")
    @Permission(value = "features.secret", message = "Access denied to secret area!")
    public void secret(CommandSender sender) {
        sender.sendMessage("secret area");
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
        public void execute(CommandSender sender) {
            sender.sendMessage("panel open");
        }

        @Command("spawn")
        public void spawn(CommandSender sender, String worldName) {
            sender.sendMessage("spawn=" + worldName);
        }

        @Command("info")
        public void info(Player sender) {
            sender.sendMessage("info=" + sender.getName());
        }
    }
}
