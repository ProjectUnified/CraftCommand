# Platform Tutorials

## Bukkit

### Command Class

```java
@Command("teleport")
@Command(value = "here", aliases = {"h"})
public class TeleportCommand {

    @Default
    public void execute(Player sender, double x, double y, double z) {
        sender.teleport(new Location(sender.getWorld(), x, y, z));
    }

    @Command("here")
    public void teleportHere(Player sender, Player target) {
        sender.teleport(target.getLocation());
    }

    @Command(value = "world")
    public void toWorld(Player sender, World world,
                        @Default("0") double x, @Default("64") double y) {
        sender.teleport(new Location(world, x, y, 0));
    }
}
```

### Registration

```java
public class MyPlugin extends JavaPlugin {
    private BukkitCommandManager manager;

    @Override
    public void onEnable() {
        manager = new BukkitCommandManager(this);
        manager.register(new TeleportCommand());
        manager.syncCommand();
    }

    @Override
    public void onDisable() {
        manager.unregisterAll();
    }
}
```

## Paper (Brigadier)

### Command Class

```java
@Command("broadcast")
public class BroadcastCommand {

    @Default
    public void execute(CommandSender sender, @Greedy String message) {
        Bukkit.broadcastMessage(message);
    }

    @Command("stack")
    public void executeStack(CommandSourceStack sender, @Greedy String message) {
        sender.getSender().sendMessage(message);
    }
}
```

### Registration

```java
public class MyPlugin extends JavaPlugin {
    private PaperCommandManager manager;

    @Override
    public void onEnable() {
        manager = new PaperCommandManager(this);
        manager.register(new BroadcastCommand());
    }
}
```

## Standalone

### Command Class

```java
@Command("calc")
public class CalculatorCommand {

    @Command("add")
    public void add(Object sender, double a, double b) {
        System.out.println("Result: " + (a + b));
    }

    @Command("sub")
    public void sub(Object sender, double a, double b) {
        System.out.println("Result: " + (a - b));
    }

    @Command("advanced")
    public static class Advanced {
        @Command("power")
        public void power(Object sender, double base, double exp) {
            System.out.println("Result: " + Math.pow(base, exp));
        }
    }
}
```

### Registration

```java
StandaloneCommandManager manager = new StandaloneCommandManager();
manager.register(new CalculatorCommand());

StandaloneCommand cmd = manager.getCommand("calc");
cmd.execute("Console", new String[]{"add", "5", "10"});
```
