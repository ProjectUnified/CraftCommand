# CraftCommand Architecture

## Overview

CraftCommand is an annotation-driven command framework for Java servers. A compile-time annotation processor generates platform-specific command wrappers, eliminating runtime reflection and providing type-safe command definitions.

## Module Layout

```
craftcommand/
├── annotation/           @Command, @Subcommand, @Default, @Resolve annotations
├── runtime/              CommandManager interface + CommandFactory<S>
├── bukkit/
│   ├── runtime/          BukkitCommandManager (CraftBukkit)
│   └── processor/        BukkitCommandProcessor → *_Executor.java
├── paper/
│   ├── runtime/          PaperCommandManager (Brigadier)
│   └── processor/        PaperCommandProcessor → *_Paper.java + *_PaperBasic.java
├── standalone/
│   ├── runtime/          StandaloneCommandManager (no Minecraft)
│   └── processor/        StandaloneCommandProcessor → *_Standalone.java
├── validation/           @Required, @Min, @Max annotations + SPI processor
├── processor/            BaseCommandProcessor + model + extension SPI
└── examples/             Example commands for each platform
```

## Annotation Processing Pipeline

```
@Command class
    ↓
BaseCommandProcessor.process()
    ↓
Build CommandModel (tree of CommandModel nodes)
    ↓
PlatformProcessor.generateWrapper()
    ↓
  ┌───────────────────────────────────────────┐
  │  Platform-independent (BaseCommandProcessor)  │
  │  • resolve_* parameter resolvers            │
  │  • filterSuggestions helper                 │
  │  • Suggestion routing (nested subcommands)  │
  │  • factory() static method                  │
  ├───────────────────────────────────────────┤
  │  Platform-specific                          │
  │  • Command class/interface                  │
  │  • Sender casting                           │
  │  • Permission checks                        │
  │  • Suggestion providers                     │
  │  • Execution source (Paper: Brigadier)      │
  └───────────────────────────────────────────┘
    ↓
JavaFile → *.java in build/generated/sources
```

## Runtime Flow

### Registration

```
Manager.register(commandInstance)
  → Class.forName(wrapperName)          // one reflection per class
  → CommandFactory wraps MethodHandle   // cached forever
  → factory.create(manager)             // zero reflection
  → platform-specific registration      // e.g., Bukkit PluginCommand
```

### Execution

```
Platform dispatches to command
  → Wrapper.execute(args)
    → resolve sender (instanceof check)
    → resolve_* methods parse args
    → call user method
    → handle exceptions
```

## Key Design Decisions

1. **Generate, don't reflect** — wrappers are plain Java, no proxy/reflection overhead
2. **One reflection per class** — `Class.forName` + `MethodHandle` cached in `CommandFactory`
3. **Platform processors are internal** — their API can break between versions
4. **SPI for extensions** — `TypeSupport`, `ParameterAnnotationHandler`, `MethodAnnotationHandler`
5. **Java 8 target** — no records, no var, no switch expressions

## Extension Points

| SPI Interface | Module | Purpose |
|---|---|---|
| `TypeResolver` | processor/extension | Custom type → argument resolver |
| `ParameterAnnotationHandler` | processor/extension | Run code for annotated parameters |
| `MethodAnnotationHandler` | processor/extension | Run code for annotated methods |
| `CommandManager` | runtime | Platform-specific command registration |
| `CommandFactory<S>` | runtime | Cached MethodHandle wrapper creation |
