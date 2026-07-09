# Architecture

## Overview

Compile-time annotation processor generates platform-specific command wrappers. No runtime reflection for command
execution.

## Module Layout

```
craftcommand/
├── annotations/        @Command, @Default, @Resolve, @Greedy, @Suggest, @Name
├── runtime/            CommandManager, ArgumentResolver, CommandInfo
├── bukkit/
│   ├── annotations/    @Permission
│   ├── runtime/        BukkitCommandManager
│   └── processor/      BukkitCommandProcessor → *_Executor
├── paper/
│   ├── runtime/        PaperCommandManager
│   └── processor/      PaperCommandProcessor → *_Paper
├── standalone/
│   ├── runtime/        StandaloneCommandManager
│   └── processor/      StandaloneCommandProcessor → *_Standalone
├── validation/
│   ├── annotations/    @Min, @Max, @ValidateWith
│   └── processor/      MinHandler, MaxHandler, ValidateWithHandler (SPI)
├── processor/          BaseCommandProcessor, model, extension SPI
└── docs/               This documentation
```

## Processing Flow

```
@Command class
  → CommandParser builds CommandModel tree
  → BaseCommandProcessor generates wrapper via template anchors
  → Platform processor fills anchors (type setup, entry methods, helpers)
  → JavaFile written to build/generated/sources
```

## Template Anchors

The base processor defines a 7-phase template. Platform processors override anchors:

| Phase | Anchor                        | Purpose                            |
|-------|-------------------------------|------------------------------------|
| 1     | `anchorConfigureType`         | Set superclass/interface           |
| 2     | `anchorAdditionalFields`      | Extra fields                       |
| 3     | `anchorConstructorTop/Bottom` | Constructor setup                  |
| 4     | `anchorBuildEntryMethods`     | execute/tabComplete/getCommandNode |
| 5     | `anchorAdditionalHelpers`     | Platform helper methods            |
| 7     | `anchorExtraMethods`          | Brigadier tree, etc.               |

## Runtime Flow

```
manager.register(new MyCommand())
  → Class.forName(suffix)
  → MethodHandle instantiation
  → Platform registration (CommandMap / LifecycleEvents / HashMap)
```

## Extension Points

| Interface                    | Module        | Purpose                              |
|------------------------------|---------------|--------------------------------------|
| `ParameterAnnotationHandler` | processor SPI | Custom parameter annotations         |
| `MethodAnnotationHandler`    | processor SPI | Custom method annotations            |
| `SuggestionProvider`         | processor SPI | Global type suggestions              |
| `CommandValidator`           | processor SPI | Execution wrapping (cooldown, async) |
| `ArgumentResolver`           | runtime       | Custom type resolution               |
| `ArgumentResolverProvider`   | runtime       | Dynamic resolver lookup              |

## Design Principles

1. **Generate, not reflect** — wrappers are plain Java
2. **MethodHandle instantiation** — no constructor reflection at runtime
3. **Java 8 target** — no records, no var
4. **SPI for extensions** — third-party annotations and suggestions
