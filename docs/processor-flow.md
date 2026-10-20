# Processor Flow

## Two Factories

The processor is two independent halves, each with its own factory:

| Phase    | Factory                 | Input             | Output            | Reads annotations |
|----------|-------------------------|-------------------|-------------------|-------------------|
| Parse    | `CommandParserFactory`  | `TypeElement`     | `CommandModel`    | yes (only here)   |
| Generate | `CommandGeneratorFactory` | `CommandModel`  | Java source file  | only SPI + platform annotations |

`BaseCommandProcessor` calls the first and the second; it holds no generation code. A platform processor is
therefore a few lines. A platform generator is one class with a handful of hooks.

## Parse Phase

`CommandParser` reads `@Command`, `@Default`, `@Resolve`, `@Name`, `@Greedy`, `@Suggest` and builds a
`CommandModel` tree (root command, nested command classes, methods, parameters).

All annotation-derived facts are resolved at this point:

- a named `@Resolve` links its `MethodModel`, including on parameters of other resolver methods
- `@Suggest` links either a provider method or marks a provider field
- diagnostics for unknown resolvers / suggest providers are reported here, not during generation

Generators therefore only read models. The remaining annotation reads are SPI handler dispatch (third-party
annotations) and platform annotations such as `@Permission`; elements are still used for types and names.

## Generate Phase

`BaseCommandGenerator.generate` runs the shared template:

```
Step 1: configureClass()                → class declaration (superclass / interfaces)
Step 2: fields                          → instance, manager, subcommand instances
Step 3: constructor                     → platform setup + nested command construction
Step 4: generateEntryMethods()          → execute / tabComplete / getCommandNode
Step 5: generateHelpers()               → routing, suggestions, sender casts, platform helpers
Step 6: buildCommandInfo()              → BaseCommand.getCommandInfo()
```

### Array-based execution (Standalone, Bukkit)

- Subcommand dispatch: `switch (args[0].toLowerCase())`, one case per subcommand and alias, nested command classes
  routed to `execute<ClassName>` helpers.
- Minimum argument check is computed at compile time:
  `if (args.length < required) throw new CommandException(...)`.
- Built-in types parse inline (`args[1]`, `args[2]`, ...). Locals are named after the declared parameters
  (`num1`, `num2`), falling back to `param_N` only when a name would shadow a generated local.
- An argument cursor (`int argIdx` / `int[] argIdxHolder`) is emitted only when a parameter can consume
  conditionally or a runtime resolver can consume a variable number of arguments.
- Custom types call `manager.resolveParameter(...)`.
- SPI handlers add validation or checks after each value is resolved.

### Brigadier execution (Paper)

- `getCommandNode()` builds a native Brigadier tree of literals and arguments.
- Paper argument factories are discovered from every no-argument `ArgumentTypes` method at processing time, so
  new paper-api argument types are supported without changing this generator. Resolver-returning factories are
  unwrapped through their `ArgumentResolver` generic type.
- `PaperExecutionSource` retrieves values from the Brigadier context (native arguments or
  `manager.resolveParameter(...)` via a `String` argument).
- Resolver parameters are resolved from their linked `MethodModel`, the same way as the array platforms.

## Adding a Platform

A platform needs two small classes. Neither needs to know how parsing works, and the generator only touches the
hooks it changes:

```java
// 1. Generator: platform settings + entry points. Everything else is inherited.
public final class MyCommandGenerator extends BaseCommandGenerator {
    public MyCommandGenerator(ProcessingEnvironment env) {
        super(env);
        // optional: senderTypeRegistry().registerSenderType("my.Sender");
        // optional: typeSupport().register(TypeSupport.Entry.builder(...)...build());
    }

    @Override protected String getWrapperClassSuffix() { return "$MyCommand"; }
    @Override public ClassName getSenderTypeName()      { return ClassName.get("my", "Sender"); }
    @Override protected TypeName getManagerType()       { return ParameterizedTypeName.get(
            ClassName.get("io.github.projectunified.craftcommand", "CommandManager"), getSenderTypeName()); }

    @Override protected void configureClass(TypeSpec.Builder type, CommandModel model) {
        type.addSuperinterface(ClassName.get("my", "MyCommand"));
    }

    @Override protected void generateEntryMethods(TypeSpec.Builder type, CommandModel model) {
        MethodSpec.Builder execute = MethodSpec.methodBuilder("execute")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(getSenderTypeName(), "sender")
                .addParameter(String[].class, "args");
        generateExecuteMethodBody(execute, model, "return true");
        type.addMethod(execute.build());
    }
}
```

```java
// 2. Processor: registers the generator factory and its service entry.
@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.projectunified.craftcommand.annotation.Command")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class MyCommandProcessor extends BaseCommandProcessor {
    @Override protected CommandGeneratorFactory getGeneratorFactory() {
        return MyCommandGenerator::new;
    }
}
```

JavaPoet knowledge needed by a platform author:

- `generateEntryMethods`: `TypeSpec`/`MethodSpec`/`ClassName` (the entry method signatures).
- `configureClass`: `TypeSpec.Builder` if the class needs a superclass or interface.
- optional hooks: `addConstructorStatements` (`MethodSpec.Builder`), `onBeforeExecute` (control flow and
  `$T`/`$S` placeholders), `generatePlatformHelpers` (`TypeSpec`/`MethodSpec`), `getSenderExpression`
  (`CodeBlock`).
- `TypeSupport.Entry`: `CodeBlock` lambdas plus the named inputs `TypeSupport.Resolution` / `TypeSupport.Suggestion`
  for platform type parsing and suggestions.

The shared generators are plain calls: `generateExecuteMethodBody`, `buildSuggestionRouting`,
`buildAliasesExpression`, `buildSenderCastHelpers`, `buildTypeSuggestionHelpers`, plus the sender/type queries.
Override `generateHelpers` only for a platform that does not use the array-based helper set (Paper does this and
calls `buildSenderCastHelpers` + `buildTypeSuggestionHelpers`). Override `onBeforeExecute` for checks that run
before any parameter is resolved.

Register the processor with the compiler like any annotation processor. `@AutoService(Processor.class)` generates
`META-INF/services/javax.annotation.processing.Processor` at compile time.

## Cross-cutting Extensions

| Component              | Purpose                                                                       |
|------------------------|-------------------------------------------------------------------------------|
| `TypeSupport`          | Built-in and platform type behaviors (width, literal, parse, suggestions)     |
| `SenderTypeRegistry`   | Sender types of a platform and how to recognize them                          |
| `SpiLoader`            | Loads `ParameterAnnotationHandler` / `MethodAnnotationHandler` via Java SPI   |
| `Naming`               | Deterministic generated member names                                          |
| `ArgCursor`            | Emits constant indices until conditional consumption requires a cursor        |

## Generated Code

- Plain Java 8, no runtime helpers required for a normal command.
- Class-level Javadoc only; helper methods rely on names and signatures.
- `COMMAND_INFO` is `Arrays.asList(...)` (wrapped once in `unmodifiableList` for the multi-entry case), or
  `singletonList` / `emptyList`.
- Execution and suggestion routing is a `switch` that reads like hand-written code.

## Errors

- Execution and validation errors throw `CommandException`; generated wrappers catch them and call
  `CommandManager.handleException(sender, exception)`.
- Generated messages always go through the manager:
  - `CommandManager.sendMessage(sender, key, defaultFormat, args...)` formats and delivers the message.
  - `handleException` sends the exception message through the same path.
  - `formatMessage` supplies the generated default; override it to translate keys.
- Default keys: `permission`, `unknown-subcommand`, `usage`, `missing-argument`, `invalid-sender`,
  `validation.min`, `validation.max`, `validation.custom`, `player-not-found`, `world-not-found`,
  `invalid-coordinate`, `no-resolver`, `error` (exception messages, with the exception message as the `%s`
  argument).
- Platform managers implement `sendMessage(sender, message)` to deliver to their sender type (console print,
  Bukkit chat, Paper component).
