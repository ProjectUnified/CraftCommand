# Platform Tutorials

## Bukkit

### Command Class

```java
@Command("hello")
@Permission("hello.use")
public class HelloCommand {

    @Default
    public void execute(CommandSender sender) {
        sender.sendMessage("Hello, World!");
    }

    @Command("player")
    public void helloPlayer(Player sender, Player target) {
        target.sendMessage(sender.getName() + " says hello!");
        sender.sendMessage("Greeted " + target.getName());
    }

    @Command("greedy")
    public void greetAll(CommandSender sender, @Greedy String message) {
        for (Player p : Bukkit.getOnlinePlayers()) {
            p.sendMessage(message);
        }
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
        manager.register(new HelloCommand());
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
@Permission("broadcast.use")
public class BroadcastCommand {

    @Default
    public void execute(CommandSourceStack sender, @Greedy String message) {
        sender.getSender().sendMessage("Broadcasting: " + message);
        Bukkit.broadcastMessage(message);
    }

    @Command("stack")
    public void executeStack(CommandSourceStack sender, @Greedy String message) {
        sender.getSender().sendMessage("Stack broadcast: " + message);
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

    @Default
    public void execute(Object sender) {
        System.out.println("Use /calc add <a> <b>");
    }

    @Command("add")
    public void add(Object sender, double a, double b) {
        System.out.println("Result: " + (a + b));
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

## Feature Examples

### Annotations on Parameters

```java
// @Default + @Min + @Max + @ValidateWith
public void setLevel(Player sender, @Min(0) @Max(100) @ValidateWith("validateEven") @Default("50") int level) { ... }

// @Greedy + @Name + @Suggest
public void chat(Player sender, @Name("text") @Greedy @Suggest("colors") @Default("red") String message) { ... }

// @Resolve with different sender types
public void resolveWithBase(CommandSender sender, @Resolve("resolveCustom") CustomType ct) { ... }
public void resolveWithPlayer(Player sender, @Resolve("resolvePoint") Point pt) { ... }
```

### Resolver Methods

```java
// No sender param
public Point resolvePoint(double x, double y) {
    return new Point(x, y);
}

// Same sender type
public Point resolvePointWithSender(Player sender, double x, double y) {
    return new Point(x + sender.getLocation().getX(), y);
}

// Base sender type
public CustomSender resolveCustom(CommandSender sender) {
    return new CustomSender(sender);
}

// With @Default on resolver params
public Point resolvePointDefault(double x, @Default("0") double y) {
    return new Point(x, y);
}
```

### Nested Subcommand Classes

```java
@Command("admin")
@Permission("admin.use")
public class AdminCommands {
    @Default
    public void execute(Player sender) {
        sender.sendMessage("Admin panel");
    }

    @Command("spawn")
    public void spawn(Player sender, String worldName) {
        sender.sendMessage("Spawn set for " + worldName);
    }
}
```
