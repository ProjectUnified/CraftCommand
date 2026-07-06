package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.standalone.StandaloneCommand;
import io.github.projectunified.craftcommand.standalone.StandaloneCommandManager;

import java.util.Arrays;
import java.util.List;

public class CLIApp {
    public static void main(String[] args) {
        StandaloneCommandManager manager = new StandaloneCommandManager();

        // Register a dynamic provider for Java Enums
        manager.registerProvider(type -> {
            if (type.isEnum()) {
                return (sender, cmdArgs, current) -> Enum.valueOf((Class<Enum>) type, current.toUpperCase());
            }
            return null;
        });

        manager.register(new CalculatorCommand());

        System.out.println("=== Standalone CraftCommand CLI Calculator ===");
        System.out.println("Available commands: calc (or c)");
        System.out.println("Try typing: calc add 5 10");
        System.out.println("Try typing: calc op multiply 3 4");
        System.out.println("Try typing: calc print custom_prefix Hello World!");
        System.out.println("To see tab completions, type: suggest calc op ");
        System.out.println("Type 'exit' or 'quit' to close.");
        System.out.println("==============================================");

        // Run direct demonstration
        System.out.println("\n--- RUNNING DEMONSTRATION ---");
        System.out.println("Executing: calc add 40 2");
        executeLine(manager, "calc add 40 2");
        System.out.println("Executing: calc sub 100 50");
        executeLine(manager, "calc sub 100 50");
        System.out.println("Executing: calc op multiply 6 7");
        executeLine(manager, "calc op multiply 6 7");
        System.out.println("Executing: calc print custom_prefix Hello World!");
        executeLine(manager, "calc print custom_prefix Hello World!");
        System.out.println("Executing: calc advanced");
        executeLine(manager, "calc advanced");
        System.out.println("Executing: calc advanced power 2 10");
        executeLine(manager, "calc advanced power 2 10");
        System.out.println("Executing: calc advanced sqrt 16");
        executeLine(manager, "calc advanced sqrt 16");
        System.out.println("Executing: calc enumop multiply 7 8");
        executeLine(manager, "calc enumop multiply 7 8");
        System.out.println("Suggesting: calc op ");
        suggestLine(manager, "calc op ");
        System.out.println("Suggesting: calc advanced ");
        suggestLine(manager, "calc advanced ");
        System.out.println("-----------------------------\n");
    }

    private static void executeLine(StandaloneCommandManager manager, String line) {
        String[] parts = line.split(" ");
        String label = parts[0];
        StandaloneCommand cmd = manager.getCommand(label);
        if (cmd == null) {
            System.out.println("Unknown command: " + label);
        } else {
            String[] cmdArgs = Arrays.copyOfRange(parts, 1, parts.length);
            cmd.execute("Console", cmdArgs);
        }
    }

    private static void suggestLine(StandaloneCommandManager manager, String line) {
        String[] parts = line.split(" ", -1);
        String label = parts[0];
        StandaloneCommand cmd = manager.getCommand(label);
        if (cmd == null) {
            System.out.println("Unknown command: " + label);
        } else {
            String[] cmdArgs = Arrays.copyOfRange(parts, 1, parts.length);
            List<String> suggestions = cmd.tabComplete("Console", cmdArgs);
            System.out.println("Suggestions: " + suggestions);
        }
    }
}
