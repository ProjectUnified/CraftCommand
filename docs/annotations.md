# Annotations

## `@Command`

Defines a command or subcommand.

- **On a class:** Main command entry point.
- **On a method/nested class:** Subcommand.

| Attribute        | Type       | Default  | Description                      |
|------------------|------------|----------|----------------------------------|
| `value`          | `String`   | required | Command name                     |
| `aliases`        | `String[]` | `{}`     | Alternative names                |
| `description`    | `String`   | `""`     | Static description               |
| `descriptionKey` | `String`   | `""`     | i18n message key for description |

```java
@Command("teleport")
@Command(value = "here", aliases = {"h"})  // on method = subcommand
```

## `@Default`

Dual-purpose: marks default method or optional parameter.

- **On methods:** Executes when no subcommand matches. `value()` must be empty.
- **On parameters:** Marks optional with a default value string.

```java
@Default
public void execute(Player sender, double x, double y, double z) { ... }

public void heal(Player sender, @Default("10") int amount) { ... }
```

## `@Resolve`

Binds a parameter to a local resolver method.

- **On methods:** Declares it as a resolver for its return type.
- **On parameters:** Binds to a resolver by name.

```java
@Resolve
public Location resolveLocation(Player sender, double x, double y, double z) { ... }

public void tp(Player sender, @Resolve("resolveLocation") Location loc) { ... }
```

Resolver methods can have any sender type (same as command, different supported type, or base type). The framework automatically casts and passes the correct sender.

## `@Name`

Overrides parameter name in usage/error messages.

```java
public void heal(Player sender, @Name("amount") int health) { ... }
// Usage: <amount>
```

## `@Greedy`

Parameter consumes all remaining arguments. Must be last.

```java
public void msg(Player sender, @Greedy String message) { ... }
```

## `@Suggest`

Binds parameter to a suggestion provider for tab-completion.

Can reference a **field** (returns `List<String>`) or a **method** (accepts 0-3 params: sender, args, current).

```java
// Field-based suggestions
public final List<String> modes = Arrays.asList("normal", "silent", "instant");
public void setMode(Player sender, @Suggest("modes") String mode) { ... }

// Method-based suggestions
public List<String> getNearPlayers(Player sender, String[] args, String current) { ... }
public void tpNear(Player sender, @Suggest("getNearPlayers") Player target) { ... }
```

## `@Permission` (Bukkit/Paper only)

Sets command permission. Works on classes and methods.

| Attribute | Type     | Default  | Description                          |
|-----------|----------|----------|--------------------------------------|
| `value`   | `String` | required | Permission node                      |
| `message` | `String` | `""`     | Custom denied message or i18n key    |

```java
@Command("admin")
@Permission("myplugin.admin")
public class AdminCommands { ... }

@Command("secret")
@Permission(value = "myplugin.secret", message = "Access denied!")
public void secret(Player sender) { ... }
```

## `@Min` / `@Max` (Validation)

Validates numeric parameters against bounds.

| Attribute | Type     | Default  | Description         |
|-----------|----------|----------|---------------------|
| `value`   | `double` | required | Min/max value       |
| `message` | `String` | `""`     | Custom error message|

```java
public void setLevel(Player sender, @Min(0) @Max(100) @Default("50") int level) { ... }
```

## `@ValidateWith` (Validation)

Custom validation method. The method must accept the parameter type and throw `IllegalArgumentException` on failure.

| Attribute | Type     | Default  | Description              |
|-----------|----------|----------|--------------------------|
| `value`   | `String` | required | Validation method name   |
| `message` | `String` | `""`     | Custom error message     |

```java
public void validateCoordinate(double coord) {
    if (Math.abs(coord) > 30000000) {
        throw new IllegalArgumentException("Coordinate cannot exceed 30,000,000!");
    }
}

public void tp(Player sender, @ValidateWith("validateCoordinate") double x, double y, double z) { ... }
```

## Annotation Combinations

Annotations can be stacked on the same parameter:

```java
// @Default + @Min + @Max + @ValidateWith (quadruple stack)
public void setLevel(Player sender, @Min(0) @Max(100) @ValidateWith("validateEven") @Default("50") int level) { ... }

// @Greedy + @Name + @Suggest + @Default (quadruple stack)
public void chat(Player sender, @Name("text") @Greedy @Suggest("colors") @Default("red") String message) { ... }

// @Resolve + @Suggest (on different params)
public void build(Player sender, @Resolve("resolvePoint") Point pt, @Suggest("blocks") String block) { ... }
```
