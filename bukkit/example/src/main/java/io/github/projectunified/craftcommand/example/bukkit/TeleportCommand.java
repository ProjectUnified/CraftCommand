package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.CommandInfo;
import io.github.projectunified.craftcommand.annotation.*;
import io.github.projectunified.craftcommand.bukkit.BukkitCommandManager;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.github.projectunified.craftcommand.validation.annotation.Max;
import io.github.projectunified.craftcommand.validation.annotation.Min;
import io.github.projectunified.craftcommand.validation.annotation.ValidateWith;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

@Command(value = "tp", aliases = {"teleport"}, description = "Comprehensive Teleport Commands")
@Permission("example.tp")
public class TeleportCommand {
    // 1. Suggestion provider from a Field
    public final List<String> modes = Arrays.asList("normal", "silent", "instant");
    private final BukkitCommandManager commandManager;

    public TeleportCommand(BukkitCommandManager commandManager) {
        this.commandManager = commandManager;
    }

    // 2. Suggestion provider from a Method (context-aware, filters players within 50 blocks)
    public List<String> getNearPlayers(Player sender, String[] args, String current) {
        List<String> suggestions = new ArrayList<>();
        String lower = current.toLowerCase();
        for (Player p : Bukkit.getOnlinePlayers()) {
            if (p.getWorld().equals(sender.getWorld()) && p.getLocation().distance(sender.getLocation()) < 50) {
                if (p.getName().toLowerCase().startsWith(lower)) {
                    suggestions.add(p.getName());
                }
            }
        }
        return suggestions;
    }

    // Use Case: @Default command with @Default object argument
    @Default
    public void execute(Player sender, @Default Player target) {
        if (target == null) {
            sender.teleport(sender.getWorld().getSpawnLocation());
            sender.sendMessage("Teleported to spawn.");
        } else {
            sender.teleport(target);
            sender.sendMessage("Teleported to " + target.getName());
        }
    }

    // Use Case: @Command with platform-specific @Permission
    @Command(value = "here", aliases = {"h"})
    @Permission("example.tp.here")
    public void teleportHere(Player sender, Player target) {
        target.teleport(sender);
        sender.sendMessage("Teleported " + target.getName() + " to you");
    }

    // Use Case: Subcommand with no arguments
    @Command(value = "all")
    @Permission("example.tp.all")
    public void teleportAll(Player sender) {
        for (Player player : Bukkit.getOnlinePlayers()) {
            if (!player.equals(sender)) {
                player.teleport(sender);
            }
        }
        sender.sendMessage("Teleported all online players to you");
    }

    // Use Case: Resolving complex Location parameter via default global resolver (width = 4)
    @Command("loc")
    public void teleportLocation(Player sender, Location location) {
        sender.teleport(location);
        sender.sendMessage(String.format("Teleported to %.2f, %.2f, %.2f in %s", location.getX(), location.getY(), location.getZ(), location.getWorld().getName()));
    }

    // Local resolver method for Location with optional World parameter
    public Location resolveLocationLocal(Player sender, double x, double y, double z, @Default World world) {
        World targetWorld = world != null ? world : sender.getWorld();
        return new Location(targetWorld, x, y, z);
    }

    // Use Case: Local parameter resolver with optional arguments (min_width = 3, max_width = 4)
    @Command("loc-local")
    public void teleportLocationLocal(Player sender, @Resolve("resolveLocationLocal") Location location) {
        sender.teleport(location);
        sender.sendMessage(String.format("Teleported (local) to %.2f, %.2f, %.2f in %s", location.getX(), location.getY(), location.getZ(), location.getWorld().getName()));
    }

    // Use Case: Subcommand with @Default primitive defaults (boolean) and validations (@Min, @Max)
    @Command(value = "player")
    public void tpPlayer(Player sender, Player target, @Min(0) @Max(1) @Default("0") int level) {
        sender.teleport(target);
        sender.sendMessage("Teleported to " + target.getName() + " with priority level " + level);
    }

    // Use Case: Subcommand with @Greedy and @Name override
    @Command(value = "msg")
    public void sendMessage(Player sender, Player target, @Name("text") @Greedy String message) {
        target.sendMessage(sender.getName() + " says: " + message);
        sender.sendMessage("Message sent to " + target.getName());
    }

    // Use Case: @Suggest referencing a class Field
    @Command(value = "mode")
    public void setMode(CommandSender sender, @Suggest("modes") String mode) {
        sender.sendMessage("Teleport mode set to: " + mode);
    }

    // Use Case: @Suggest referencing a class Method
    @Command(value = "near")
    public void tpNear(Player sender, @Suggest("getNearPlayers") Player target) {
        sender.teleport(target);
        sender.sendMessage("Teleported to nearby player: " + target.getName());
    }

    // Use Case: No @Sender annotation; index 0 parameter acts as the sender resolved/cast automatically
    @Command(value = "info")
    public void getInfo(Player player) {
        player.sendMessage("Your location is: " + player.getLocation());
    }

    // Use Case: @Command with a custom permission message
    @Command("secret")
    @Permission(value = "example.tp.secret", message = "Access denied! The secret TP area is off-limits.")
    public void secretTp(Player sender) {
        sender.sendMessage("Welcome to the secret teleport area.");
    }

    // Custom validation method for coordinate
    public void validateCoordinate(double coord) {
        if (Math.abs(coord) > 30000000) {
            throw new IllegalArgumentException("Coordinate cannot exceed 30,000,000!");
        }
    }

    // Use Case: Custom validation annotation Showcase
    @Command("tp-coord")
    public void tpCoordinate(Player sender, @ValidateWith("validateCoordinate") double x, double y, double z) {
        sender.teleport(new Location(sender.getWorld(), x, y, z));
        sender.sendMessage(String.format("Teleported to custom validated coordinates: %.2f, %.2f, %.2f", x, y, z));
    }

    @Command("help")
    public void getHelp(CommandSender sender) {
        sender.sendMessage("--- Teleport Command Help ---");
        for (CommandInfo info : commandManager.getCommandInfo(this)) {
            String path = String.join(" ", info.getPath());
            String usage = info.getUsage();
            String desc = info.getDescription();
            sender.sendMessage("/" + path + (usage.isEmpty() ? "" : " " + usage) + (desc.isEmpty() ? "" : " - " + desc));
        }
    }

    // Use Case: Nested subcommand class (inner class)
    @Command("admin")
    @Permission("example.tp.admin")
    public class AdminCommands {
        @Default
        public void execute(Player sender) {
            sender.sendMessage("Teleport admin console.");
        }

        @Command("spawn")
        public void setSpawn(Player sender, String worldName) {
            sender.sendMessage("Spawn for world " + worldName + " has been set.");
        }
    }
}
