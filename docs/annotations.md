# Annotation Reference

CraftCommand uses annotations to model command execution, parameter validation, and user suggestion handling. Here is a complete reference of the available annotations.

---

## Command Annotations

### `@Command`
* **Target:** Type (Class)
* **Description:** Identifies a class as a main command. The processor will generate a wrapper executor for this class.
* **Attributes:**
  * `value` (String): The primary name of the command.
* **Example:**
  ```java
  @Command("teleport")
  public class TeleportCommand { ... }
  ```

### `@Subcommand`
* **Target:** Method, Nested Class
* **Description:** Identifies a method or nested static class as a subcommand of the parent command.
* **Attributes:**
  * `value` (String): The subcommand name.
* **Example:**
  ```java
  @Subcommand("player")
  public void toPlayer(Player sender, Player target) { ... }
  ```

### `@Default`
* **Target:** Method
* **Description:** Designates a method as the default subcommand handler. It is executed if no subcommand matching any `@Subcommand` is specified by the user.
* **Example:**
  ```java
  @Default
  public void execute(Player sender, double x, double y, double z) { ... }
  ```

---

## Parameter Annotations

### `@Optional`
* **Target:** Parameter
* **Description:** Marks a command parameter as optional. If the user doesn't provide this argument, the specified default value is used.
* **Attributes:**
  * `value` (String): The default value string to parse if missing.
* **Example:**
  ```java
  public void heal(Player sender, @Optional("10") int amount) { ... }
  ```

### `@Description`
* **Target:** Type, Method, Parameter
* **Description:** Provides a human-readable description for commands, subcommands, or individual parameters. This is used when displaying automatically generated help guides.
* **Attributes:**
  * `value` (String): The description string.
* **Example:**
  ```java
  @Description("Heals a player's health points")
  public void heal(Player sender, @Description("Health points to restore") int amount) { ... }
  ```

### `@Alias`
* **Target:** Type, Method
* **Description:** Declares aliases (alternative names) for commands or subcommands.
* **Attributes:**
  * `value` (String[]): An array of alias strings.
* **Example:**
  ```java
  @Command("teleport")
  @Alias({"tp", "tele"})
  public class TeleportCommand { ... }
  ```

### `@Condition`
* **Target:** Parameter
* **Description:** Attaches validation conditions or restrictions to a parameter (e.g. min/max bounds for numerical parameters).
* **Attributes:**
  * `value` (String): The validation condition expression.
* **Example:**
  ```java
  public void heal(Player sender, @Condition("value > 0") int amount) { ... }
  ```

### `@Suggest`
* **Target:** Parameter
* **Description:** Attaches a tab-completion suggestion provider to a command parameter.
* **Attributes:**
  * `value` (String): The suggestion provider identifier or inline suggestions.
* **Example:**
  ```java
  public void setSpeed(Player sender, @Suggest("1|2|3|4|5") int speed) { ... }
  ```
