# Processor Flow

## How It Works

The annotation processor scans `@Command` classes at compile time and generates wrapper classes. Each platform
(Standalone, Bukkit, Paper) has its own processor that extends `BaseCommandProcessor` to produce platform-specialized
wrapper code.

---

## Processing Steps

1. **Parse** — `CommandParser` reads `@Command`, `@Default`, `@Resolve`, `@Name`, `@Greedy`, `@Suggest`, and other
   annotations into a hierarchical `CommandModel` tree (root command, nested subcommand classes, methods, and
   parameters).
2. **Template Orchestration** — `BaseCommandProcessor.buildWrapperClass` runs a 6-step code generation pipeline.
3. **Platform Specialization** — Concrete platform processors (`StandaloneCommandProcessor`, `BukkitCommandProcessor`,
   `PaperCommandProcessor`) override template hooks to configure platform interfaces, constructor arguments, entry
   methods, and helpers.
4. **Generate** — JavaPoet writes the wrapper `.java` file with explicit imports and clean syntax.

---

## Code Generation Pipeline Steps

```
Step 1: configureClass()                → Set class declaration, superclasses & BaseCommand interface
Step 2: addPlatformFields()             → Declare command instance, manager, and platform-specific fields
Step 3: addConstructorStatements()      → Constructor setup & nested subcommand instantiation
Step 4: generateEntryMethods()          → Platform entry points (execute, tabComplete, or getCommandNode)
Step 5: generateHelpers()               → Subcommand execution routing, suggestions, sender casting
Step 6: buildCommandInfo()              → BaseCommand.getCommandInfo() metadata exposer
```

---

## Execution Routing & Parameter Resolution

### 1. Array-Based Execution (Bukkit & Standalone)

- **Subcommand Dispatch**: When arguments are provided, `switch (args[0].toLowerCase())` matches subcommands and nested
  subcommand classes with O (1) jump table performance.
- **Minimum Argument Validation**: Computes required parameter count at compile time and checks
  `if (args.length < required) throw new CommandException(...)` before resolving parameters.
- **Parameter Resolution**:
    - Built-in types (primitives, strings, standard wrappers) use fast, inline `TypeSupport` parsing.
    - Local `@Resolve` methods call the resolver method directly on the command instance.
    - Custom types invoke `manager.resolveParameter(...)` via registered `ArgumentResolver`s.
- **Dynamic Indexing**: If a parameter uses a multi-width or dynamic resolver, the processor uses an
  `int[] argIdxHolder = { offset }` to let resolvers consume variable numbers of arguments; otherwise, a simple
  `int argIdx = offset` is used.
- **SPI Validation**: Runs `ParameterAnnotationHandler` (e.g. `@Min`, `@Max`, `@ValidateWith`) and
  `MethodAnnotationHandler` checks.

### 2. Brigadier Node-Based Execution (Paper)

- **Tree Construction**: Builds a native Brigadier tree (`LiteralArgumentBuilder` and `RequiredArgumentBuilder`) at
  initialization time.
- **Execution Source**: Parameters are resolved from the Brigadier context using `PaperExecutionSource` (native
  Brigadier arguments or `manager.resolveParameter(...)`).

---

## Key Components

| Component              | Purpose                                                                                      |
|------------------------|----------------------------------------------------------------------------------------------|
| `CommandParser`        | Reads annotations and builds `CommandModel`, `MethodModel`, and `ParameterModel`.            |
| `BaseCommandProcessor` | Orchestrates code generation template, array execution routing, and suggestion helpers.      |
| `TypeSupport`          | Built-in type mappings for parsing, literals, and default suggestions.                       |
| `ResolverLookup`       | Discovers local `@Resolve` methods and `@Suggest` providers in command classes.              |
| `SpiLoader`            | Discovers compile-time extensions (`ParameterAnnotationHandler`, `MethodAnnotationHandler`). |
| `SenderTypeRegistry`   | Validates and tracks platform-specific sender types.                                         |
| `Naming`               | Generates deterministic, sanitized identifiers for generated fields and helper methods.      |

---

## Generated Wrappers

Each `@Command` class produces a single wrapper class implementing `BaseCommand`:

| Platform       | Suffix               | Extends / Implements                                   |
|----------------|----------------------|--------------------------------------------------------|
| **Standalone** | `$StandaloneCommand` | `implements StandaloneCommand` (extends `BaseCommand`) |
| **Bukkit**     | `$BukkitCommand`     | `extends Command implements BaseCommand`               |
| **Paper**      | `$PaperCommand`      | `implements PaperCommand` (extends `BaseCommand`)      |

---

## Error Handling & Formatting

- All command execution and validation errors throw `CommandException`.
- `CommandManager.formatMessage(key, defaultFormat, args...)` provides centralized, pluggable message formatting and
  localization (`usage`, `unknown-subcommand`, `invalid-argument`, `missing-argument`).
