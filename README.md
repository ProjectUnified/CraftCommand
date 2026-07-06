# CraftCommand

An opinionated annotation-driven command framework for Java applications.

## Difference from other frameworks

The only difference is that while others rely on runtime reflection to fetch and handle annotations, 
this framework constructs the native code with annotation processor and JavaPoet, 
allows near native speed with minimal reflection usage.

## Installation

It is recommended to import `craftcommand-bom` in your `<dependencyManagement>` section to manage version configurations of CraftCommand modules:

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

Then, add the annotation and runtime dependency to your `pom.xml` without specifying a version:

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

And configure the annotation processor in your compiler plugin:

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

## Quick Start (Standalone Console App)

### 1. Define your Command Class

```java
package myapp;

import io.github.projectunified.craftcommand.annotation.*;

@Command(value = "calc", description = "Simple Calculator Command")
public class CalculatorCommand {

    @Default
    public void defaultAction(Object sender) {
        System.out.println("Use /calc add <num1> <num2>");
    }

    @Subcommand(value = "add", aliases = {"sum"})
    public void add(Object sender, 
                    @Min(value = 0, message = "Inputs must be positive") double a, 
                    double b) {
        System.out.println("Result: " + (a + b));
    }
}
```

### 2. Register & Execute in your App

```java
import io.github.projectunified.craftcommand.standalone.StandaloneCommandManager;
import io.github.projectunified.craftcommand.standalone.StandaloneCommand;

public class Main {
    public static void main(String[] args) {
        StandaloneCommandManager manager = new StandaloneCommandManager();
        
        // Register the annotated command instance
        manager.register(new CalculatorCommand());
        
        // Customizing translation keys/messages in the dictionary
        manager.setMessage("validation.min", "Error: %s cannot be less than %s!");
        
        // Execute the command
        StandaloneCommand cmd = manager.getCommand("calc");
        cmd.execute("sender", new String[]{"add", "5.2", "10.0"}); // Prints: Result: 15.2
    }
}
```
