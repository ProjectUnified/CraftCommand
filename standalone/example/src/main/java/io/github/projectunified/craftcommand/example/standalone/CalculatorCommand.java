package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.annotation.*;
import io.github.projectunified.craftcommand.validation.annotation.Max;
import io.github.projectunified.craftcommand.validation.annotation.Min;
import io.github.projectunified.craftcommand.validation.annotation.ValidateWith;

import java.util.Arrays;
import java.util.List;

/**
 * Comprehensive example command demonstrating all CraftCommand features.
 *
 * <p>Features demonstrated:
 * <ul>
 *   <li>{@link Command} with name, aliases, description</li>
 *   <li>{@link Default} action method</li>
 *   <li>{@link Subcommand} on methods and nested classes</li>
 *   <li>{@link Optional} parameters with default values</li>
 *   <li>{@link Greedy} parameters (String and non-String types)</li>
 *   <li>{@link Name} parameter name override</li>
 *   <li>{@link Suggest} for tab completion (field and method)</li>
 *   <li>{@link Resolve} for custom parameter resolution (implicit and explicit)</li>
 *   <li>{@link Min}, {@link Max} validation annotations</li>
 *   <li>{@link ValidateWith} custom validation</li>
 *   <li>Nested subcommand classes</li>
 *   <li>Multiple sender types</li>
 * </ul>
 */
@Command(value = "calc", aliases = {"c"}, description = "A comprehensive CLI calculator demonstrating all CraftCommand features")
public class CalculatorCommand {

    // ── Suggestion Providers ──

    /**
     * Field-based suggestion provider for operator names.
     */
    public final List<String> operators = Arrays.asList("add", "subtract", "multiply", "divide");

    /**
     * Method-based suggestion provider with sender, args, and current input.
     *
     * @param sender  the command sender
     * @param args    the command arguments
     * @param current the current input being typed
     * @return list of suggested modes
     */
    @SuppressWarnings("unused")
    public List<String> getModes(Object sender, String[] args, String current) {
        return Arrays.asList("basic", "scientific", "programmer");
    }

    // ── Custom Resolvers ──

    /**
     * Local resolver for CustomSender type.
     */
    public CustomSender resolveSender(Object sender) {
        return new CustomSender(sender.toString());
    }

    /**
     * Local resolver for Point type (multi-arg, width=2).
     */
    public Point resolvePoint(double x, @Optional("0") double y) {
        return new Point(x, y);
    }

    // ── Custom Validators ──

    /**
     * Validates that divisor is not zero.
     */
    public void validateDivider(int num) {
        if (num == 0) {
            throw new IllegalArgumentException("Cannot divide by zero!");
        }
    }

    /**
     * Validates coordinate is within bounds.
     */
    public void validateCoordinate(double value) {
        if (value < -1000 || value > 1000) {
            throw new IllegalArgumentException("Coordinate out of bounds: " + value);
        }
    }

    // ── Default Action ──

    /**
     * Default action: addition of two numbers. Usage: /calc &lt;num1&gt; &lt;num2&gt;
     */
    @Default
    public void execute(Object sender, int num1, int num2) {
        System.out.println("Result: " + (num1 + num2));
    }

    // ── Basic Arithmetic Subcommands ──

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

    // ── @Suggest Feature Demo ──

    /**
     * Demonstrates field-based @Suggest for operator selection.
     */
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

    /**
     * Demonstrates method-based @Suggest with sender, args, current parameters.
     */
    @Subcommand(value = "mode")
    public void runMode(Object sender, @Suggest("getModes") String mode) {
        System.out.println("Mode set to: " + mode);
    }

    // ── @Optional Feature Demo ──

    /**
     * Demonstrates @Optional with default value on String.
     */
    @Subcommand(value = "print")
    public void print(Object sender, @Optional("Result:") String prefix, @Greedy String text) {
        System.out.println(prefix + " " + text);
    }

    /**
     * Demonstrates @Optional with default value on int.
     */
    @Subcommand(value = "repeat")
    public void repeat(Object sender, String text, @Optional("1") int count) {
        for (int i = 0; i < count; i++) {
            System.out.println(text);
        }
    }

    // ── @Greedy Feature Demo ──

    /**
     * Demonstrates @Greedy on String (joins remaining args with spaces).
     */
    @Subcommand(value = "echo")
    public void echo(Object sender, @Greedy String message) {
        System.out.println(message);
    }

    /**
     * Demonstrates @Greedy on non-String type (joins remaining args, then parses).
     */
    @Subcommand(value = "parse")
    public void parse(Object sender, @Greedy double value) {
        System.out.println("Parsed: " + value);
    }

    /**
     * Demonstrates @Greedy on array type (creates array from remaining args).
     */
    @Subcommand(value = "sum")
    public void sum(Object sender, @Greedy int[] numbers) {
        int total = 0;
        for (int n : numbers) total += n;
        System.out.println("Sum: " + total);
    }

    // ── @Name Feature Demo ──

    /**
     * Demonstrates @Name to override parameter name in usage messages.
     */
    @Subcommand(value = "msg")
    public void sendMessage(Object sender, String target, @Name("text") @Greedy String message) {
        System.out.println("To " + target + ": " + message);
    }

    // ── @Resolve Feature Demo ──

    /**
     * Demonstrates parameter-level @Resolve with named method.
     */
    @Subcommand(value = "point")
    public void runPoint(Object sender, @Resolve("resolvePoint") Point pt) {
        System.out.println("Point: (" + pt.x + ", " + pt.y + ")");
    }

    /**
     * Demonstrates sender-level @Resolve.
     */
    @Subcommand("whoami")
    public void whoAmI(@Resolve("resolveSender") CustomSender sender) {
        System.out.println("You are: " + sender.getName());
    }

    // ── @ValidateWith Feature Demo ──

    /**
     * Demonstrates @ValidateWith with custom validation method.
     */
    @Subcommand(value = "coord")
    public void setCoordinate(Object sender,
                              @ValidateWith("validateCoordinate") double x,
                              @ValidateWith("validateCoordinate") double y,
                              @ValidateWith("validateCoordinate") double z) {
        System.out.println("Location set to: (" + x + ", " + y + ", " + z + ")");
    }

    // ── @Min / @Max Feature Demo ──

    /**
     * Demonstrates @Min and @Max validation on numeric types.
     */
    @Subcommand(value = "level")
    public void setLevel(Object sender,
                         @Min(1) @Max(100) int level,
                         @Min(0) @Max(10) double multiplier) {
        System.out.println("Level " + level + " with multiplier " + multiplier);
    }

    // ── Enum Parameter Demo ──

    /**
     * Demonstrates enum parameter resolution (via registerProvider in CLIApp).
     */
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

    // ── Nested Subcommand Class ──

    public enum MathOp {
        ADD, SUBTRACT, MULTIPLY, DIVIDE
    }

    // ── Inner Types ──

    public static class CustomSender {
        private final String name;

        public CustomSender(String name) {
            this.name = name;
        }

        public String getName() {
            return name;
        }
    }

    public static class Point {
        public final double x;
        public final double y;

        public Point(double x, double y) {
            this.x = x;
            this.y = y;
        }
    }

    /**
     * Nested subcommand class demonstrating class-level subcommands.
     * Usage: /calc advanced, /calc advanced power 2 8, /calc advanced sqrt 16
     */
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

        @Subcommand("sqrt")
        public void sqrt(Object sender, @Min(0) double number) {
            System.out.println("Result: " + Math.sqrt(number));
        }

        /**
         * Demonstrates nested class with its own @Optional parameters.
         */
        @Subcommand("log")
        public void logarithm(Object sender, double value, @Optional("10") double base) {
            System.out.println("Result: " + (Math.log(value) / Math.log(base)));
        }
    }
}
