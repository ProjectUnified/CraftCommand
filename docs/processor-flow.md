# Processor Flow

## How It Works

The annotation processor scans `@Command` classes at compile time and generates wrapper classes. Each platform has its
own processor that produces platform-specific wrappers.

## Processing Steps

1. **Parse** — `CommandParser` reads `@Command`, `@Default`, `@Resolve` annotations into a `CommandModel` tree
2. **Template** — `BaseCommandProcessor.buildWrapperClass` runs a 7-phase template
3. **Anchors** — Platform processor fills in platform-specific code at each anchor
4. **Generate** — JavaPoet writes the wrapper `.java` file

## Template Phases

```
Phase 1: anchorConfigureType()          → class declaration
Phase 2: anchorAdditionalFields()       → extra fields
Phase 3: anchorConstructorTop/Bottom()  → constructor
Phase 4: anchorBuildEntryMethods()      → execute/tabComplete
Phase 5: anchorAdditionalHelpers()      → platform helpers
Phase 6: buildCommandInfoExposer()      → shared
Phase 7: anchorExtraMethods()           → Brigadier tree
```

## ExecutionSource

Platform-specific parameter resolution:

- **ArrayExecutionSource** — Bukkit/Standalone: `args[index]` array access
- **PaperExecutionSource** — Paper: Brigadier `ctx.getArgument()` retrieval

## Key Components

| Component        | Purpose                                                             |
|------------------|---------------------------------------------------------------------|
| `CommandParser`  | Reads annotations into `CommandModel`                               |
| `TypeSupport`    | Maps types to parse/literal/suggestion lambdas                      |
| `ResolverLookup` | Finds `@Resolve` methods and suggest providers                      |
| `SpiLoader`      | Loads `ParameterAnnotationHandler`, `MethodAnnotationHandler`, etc. |
| `Naming`         | Generates unique identifiers for methods/fields                     |

## Error Handling

All validation errors throw `CommandException`. The `CommandManager.formatMessage()` method handles i18n. Override it
for translation support.
