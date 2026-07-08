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

Binds parameter to a suggestion provider method.

```java
@Suggest("getModes")
public void setMode(Player sender, String mode) { ... }

public List<String> getModes(Player sender, String[] args, String current) { ... }
```
