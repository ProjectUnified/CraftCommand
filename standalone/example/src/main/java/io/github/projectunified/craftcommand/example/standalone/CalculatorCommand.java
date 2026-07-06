package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.annotation.*;
import io.github.projectunified.craftcommand.validation.annotation.Min;
import io.github.projectunified.craftcommand.validation.annotation.ValidateWith;

import java.util.Arrays;
import java.util.List;

@Command(value = "calc", aliases = {"c"}, description = "A simple CLI calculator demonstrating CraftCommand")
public class CalculatorCommand {

    // Operators list for suggestions
    public final List<String> operators = Arrays.asList("add", "subtract", "multiply", "divide");

    // Custom Resolver for Sender
    public CustomSender resolveSender(Object sender) {
        return new CustomSender(sender.toString());
    }

    // Custom Validation Method for validateDivider
    public void validateDivider(int num) {
        if (num == 0) {
            throw new IllegalArgumentException("Cannot divide by zero!");
        }
    }

    // Default action: addition of two numbers
    @Default
    public void execute(Object sender, int num1, int num2) {
        System.out.println("Result: " + (num1 + num2));
    }

    @Subcommand(value = "add")
    public void add(Object sender, int num1, int num2) {
        System.out.println("Result: " + (num1 + num2));
    }

    @Subcommand(value = "sub", aliases = {"subtract"})
    public void subtract(Object sender, int num1, int num2) {
        System.out.println("Result: " + (num1 - num2));
    }

    @Subcommand(value = "mul", aliases = {"multiply"})
    public void multiply(Object sender, int num1, int num2) {
        System.out.println("Result: " + (num1 * num2));
    }

    @Subcommand(value = "div", aliases = {"divide"})
    public void divide(Object sender, int num1, @ValidateWith("validateDivider") int num2) {
        System.out.println("Result: " + ((double) num1 / num2));
    }

    // Subcommand showcasing custom tab completions from a Field
    @Subcommand(value = "op")
    public void runOp(Object sender, @Suggest("operators") String op, int num1, int num2) {
        switch (op.toLowerCase()) {
            case "add":
                add(sender, num1, num2);
                break;
            case "subtract":
                subtract(sender, num1, num2);
                break;
            case "multiply":
                multiply(sender, num1, num2);
                break;
            case "divide":
                divide(sender, num1, num2);
                break;
            default:
                throw new IllegalArgumentException("Unknown operator: " + op);
        }
    }

    // Subcommand showcasing @Optional and @Greedy parameter matching
    @Subcommand(value = "print")
    public void print(Object sender, @Optional("Result:") String prefix, @Greedy String text) {
        System.out.println(prefix + " " + text);
    }

    @Subcommand("enumop")
    public void enumOp(Object sender, MathOp op, int num1, int num2) {
        switch (op) {
            case ADD:
                add(sender, num1, num2);
                break;
            case SUBTRACT:
                subtract(sender, num1, num2);
                break;
            case MULTIPLY:
                multiply(sender, num1, num2);
                break;
            case DIVIDE:
                divide(sender, num1, num2);
                break;
        }
    }

    // Use Case: Custom resolver for parameter 0 (the sender)
    @Subcommand("whoami")
    public void whoAmI(@Resolve("resolveSender") CustomSender sender) {
        System.out.println("You are: " + sender.getName());
    }

    // Dynamic type demo: Enum parameter using registerProvider in CLIApp
    public enum MathOp {
        ADD, SUBTRACT, MULTIPLY, DIVIDE
    }

    public static class CustomSender {
        private final String name;

        public CustomSender(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    // Use Case: Nested subcommand class (inner class) with validation annotations (@Min, @Max)
    @Subcommand("advanced")
    public class AdvancedCommands {
        @Default
        public void execute(Object sender) {
            System.out.println("Advanced operations: power, sqrt");
        }

        @Subcommand("power")
        public void power(Object sender, double base, double exponent) {
            System.out.println("Result: " + Math.pow(base, exponent));
        }

        // Showcases @Min validation annotation on parameter
        @Subcommand("sqrt")
        public void sqrt(Object sender, @Min(0) double number) {
            System.out.println("Result: " + Math.sqrt(number));
        }
    }
}
