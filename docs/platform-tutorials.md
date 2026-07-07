# Platform Implementation Tutorials

This tutorial shows how to implement and register commands using CraftCommand for both the **Bukkit (Minecraft)** platform and the **Standalone (Java CLI)** platform.

---

## 1. Bukkit Implementation Tutorial

In Minecraft Bukkit/Spigot plugins, writing commands involves parsing arguments like online players, worlds, coordinates, and dealing with sender permissions. CraftCommand automates this.

### Step A: Write the Command Class
Mark your command class with `@Command` and define subcommand methods using `@Subcommand`.

```java
package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.annotations.*;
import org.bukkit.Location;
import org.bukkit.World;
import org.bukkit.command.CommandSender;
import org.bukkit.entity.Player;

@Command("teleport")
@Alias("tp")
@Description("A powerful teleportation command")
public class TeleportCommand {

    // Default subcommand (executes when running /teleport directly)
    @Default
    @Description("Teleports the sender to coordinates")
    public void execute(Player sender, double x, double y, double z) {
        Location loc = new Location(sender.getWorld(), x, y, z);
        sender.teleport(loc);
        sender.sendMessage("Teleported to coordinates: " + x + ", " + y + ", " + z);
    }

    // A subcommand with arguments: /teleport player <target>
    @Subcommand("player")
    @Description("Teleports you to another player")
    public void toPlayer(Player sender, Player target) {
        sender.teleport(target.getLocation());
        sender.sendMessage("Teleported to player: " + target.getName());
    }

    // A subcommand with optional arguments: /teleport world <world> [x] [y] [z]
    @Subcommand("world")
    @Description("Teleports you to a specific world and coordinates")
    public void toWorld(
            Player sender,
            World world,
            @Optional("0") double x,
            @Optional("64") double y,
            @Optional("0") double z
    ) {
        Location loc = new Location(world, x, y, z);
        sender.teleport(loc);
        sender.sendMessage("Teleported to world " + world.getName() + " at " + x + ", " + y + ", " + z);
    }
}
```

### Step B: Register the Command in your Plugin
Instantiate a `BukkitCommandManager` in your plugin's `JavaPlugin` class and register the command.

```java
package io.github.projectunified.craftcommand.example.bukkit;

import io.github.projectunified.craftcommand.bukkit.BukkitCommandManager;
import org.bukkit.plugin.java.JavaPlugin;

public class ExamplePlugin extends JavaPlugin {
    private BukkitCommandManager commandManager;

    @Override
    public void onEnable() {
        // Initialize the manager
        this.commandManager = new BukkitCommandManager(this);

        // Register your command class instance
        commandManager.register(new TeleportCommand());

        // Synchronize registered commands with the server
        commandManager.syncCommand();
    }

    @Override
    public void onDisable() {
        // Unregister all commands when disabled
        this.commandManager.unregisterAll();
    }
}
```

---

## 2. Standalone Implementation Tutorial

CraftCommand can also be used in pure Java applications (CLIs, chatbots, Discord bots, etc.) that do not run on Minecraft or Bukkit servers.

### Step A: Write the Command Class
Define the standalone commands using Java standard types and custom objects.

```java
package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.annotations.*;

@Command("calc")
@Alias("c")
@Description("A simple command-line calculator")
public class CalculatorCommand {

    // Subcommand: calc add <num1> <num2>
    @Subcommand("add")
    @Description("Adds two numbers together")
    public void add(Object sender, double num1, double num2) {
        double result = num1 + num2;
        System.out.println("Result: " + result);
    }

    // Subcommand: calc sub <num1> <num2>
    @Subcommand("sub")
    @Description("Subtracts the second number from the first")
    public void sub(Object sender, double num1, double num2) {
        double result = num1 - num2;
        System.out.println("Result: " + result);
    }

    // Nested subcommands: calc advanced power <base> <exponent>
    @Subcommand("advanced")
    public static class AdvancedCommands {
        @Subcommand("power")
        public void power(Object sender, double base, double exponent) {
            double result = Math.pow(base, exponent);
            System.out.println("Result: " + result);
        }
    }
}
```

### Step B: Register and Execute Commands in your CLI App
Use `StandaloneCommandManager` to register commands, retrieve them, and pass user-entered text arguments.

```java
package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.standalone.StandaloneCommand;
import io.github.projectunified.craftcommand.standalone.StandaloneCommandManager;
import java.util.Arrays;
import java.util.List;

public class CLIApp {
    public static void main(String[] args) {
        StandaloneCommandManager manager = new StandaloneCommandManager();

        // Register the command
        manager.register(new CalculatorCommand());

        // How to execute user input:
        String inputLine = "calc add 5 10";
        String[] parts = inputLine.split(" ");
        String label = parts[0];
        String[] commandArgs = Arrays.copyOfRange(parts, 1, parts.length);

        StandaloneCommand cmd = manager.getCommand(label);
        if (cmd != null) {
            cmd.execute("Console", commandArgs); // Outputs "Result: 15.0"
        }

        // How to get tab completions programmatically:
        List<String> suggestions = cmd.tabComplete("Console", new String[]{"ad"});
        System.out.println("Suggestions: " + suggestions); // Outputs "[add]"
    }
}
```
