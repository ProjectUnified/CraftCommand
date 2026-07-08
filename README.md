# CraftCommand

Compile-time command framework for Java. Generates platform-specific wrappers via annotation processing — no runtime
reflection for execution.

## Installation

Use the BOM for version management:

```xml
<dependencyManagement>
    <dependencies>
        <dependency>
            <groupId>io.github.projectunified</groupId>
            <artifactId>craftcommand-bom</artifactId>
            <version>VERSION</version>
            <type>pom</type>
            <scope>import</scope>
        </dependency>
    </dependencies>
</dependencyManagement>
```

Add annotations + runtime + processor:

```xml
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>craftcommand-annotations</artifactId>
</dependency>
<dependency>
    <groupId>io.github.projectunified</groupId>
    <artifactId>craftcommand-standalone-runtime</artifactId>
</dependency>
```

```xml
<plugin>
    <groupId>org.apache.maven.plugins</groupId>
    <artifactId>maven-compiler-plugin</artifactId>
    <configuration>
        <annotationProcessorPaths>
            <path>
                <groupId>io.github.projectunified</groupId>
                <artifactId>craftcommand-standalone-processor</artifactId>
                <version>VERSION</version>
            </path>
        </annotationProcessorPaths>
    </configuration>
</plugin>
```

## Quick Start

```java
@Command("calc")
public class CalculatorCommand {

    @Default
    public void execute(Object sender) {
        System.out.println("Use /calc add <a> <b>");
    }

    @Command(value = "add", aliases = {"sum"})
    public void add(Object sender, double a, double b) {
        System.out.println("Result: " + (a + b));
    }
}
```

```java
StandaloneCommandManager manager = new StandaloneCommandManager();
manager.register(new CalculatorCommand());

StandaloneCommand cmd = manager.getCommand("calc");
cmd.execute("sender", new String[]{"add", "5", "10"}); // Result: 15.0
```

## Supported Platforms

| Platform          | Runtime                           | Processor                            |
|-------------------|-----------------------------------|--------------------------------------|
| Bukkit/Spigot     | `craftcommand-bukkit-runtime`     | `craftcommand-bukkit-processor`      |
| Paper (Brigadier) | `craftcommand-paper-runtime`      | `craftcommand-paper-processor`       |
| Paper (Basic)     | `craftcommand-paper-runtime`      | `craftcommand-paper-processor-basic` |
| Standalone        | `craftcommand-standalone-runtime` | `craftcommand-standalone-processor`  |

## Documentation

- [Annotations Reference](docs/annotations.md)
- [Architecture](docs/architecture.md)
- [Processor Flow](docs/processor-flow.md)
- [Platform Tutorials](docs/platform-tutorials.md)
