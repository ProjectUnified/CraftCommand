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
│   └── processor/      BukkitCommandProcessor → *$BukkitCommand
├── paper/
│   ├── runtime/        PaperCommandManager
│   └── processor/      PaperCommandProcessor → *$PaperCommand
├── standalone/
│   ├── runtime/        StandaloneCommandManager
│   └── processor/      StandaloneCommandProcessor → *$StandaloneCommand
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

## Code Generation Pipeline

The base processor defines a 6-step template. Platform processors override hooks:

| Step | Method Hook                   | Purpose                            |
|------|-------------------------------|------------------------------------|
| 1    | `configureClass`              | Set superclass/interface           |
| 2    | `addPlatformFields`           | Extra fields                       |
| 3    | `addConstructorStatements`    | Constructor setup & subcommands    |
| 4    | `generateEntryMethods`        | execute/tabComplete/getCommandNode |
| 5    | `generateHelpers`             | Subcommand routing & helpers       |
| 6    | `buildCommandInfo`            | BaseCommand.getCommandInfo()       |

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
| `ArgumentResolver`           | runtime       | Custom type resolution & suggestions |
| `ArgumentResolverProvider`   | runtime       | Dynamic resolver lookup              |

## Design Principles

1. **Generate, not reflect** — wrappers are plain Java
2. **MethodHandle instantiation** — no constructor reflection at runtime
3. **Java 8 target** — no records, no var
4. **SPI for extensions** — third-party annotations and suggestions
