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
 *   <li>{@link Command} on methods and nested classes</li>
 *   <li>{@link Default} parameters with default values</li>
 *   <li>{@link Greedy} parameters (String and non-String types)</li>
 *   <li>{@link Name} parameter name override</li>
 *   <li>{@link Suggest} for tab completion (field and method)</li>
 *   <li>{@link Resolve} for custom parameter resolution (implicit and explicit)</li>
 *   <li>{@link Min}, {@link Max} validation annotations</li>
 *   <li>{@link ValidateWith} custom validation</li>
 *   <li>Nested subcommand classes</li>
 *   <li>{@link Resolve} from inner class referencing outer class method</li>
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
     * Field-based suggestion provider for mode names.
     */
    public final List<String> modes = Arrays.asList("basic", "scientific", "programmer");

    /**
     * Method-based suggestion provider with current input.
     *
     * @param current the current input being typed
     * @return list of suggested modes
     */
    @SuppressWarnings("unused")
    public java.util.Collection<String> getModes(String[] current) {
        return Arrays.asList("basic", "scientific", "programmer");
    }

    // ── Custom Resolvers ──

    /**
     * Local resolver for CustomSender type.
     */
    public CustomSender resolveSender(Object sender) {
        return new CustomSender(sender.toString(), sender);
    }

    /**
     * Local resolver for Point type (multi-arg, width=2).
     */
    public Point resolvePoint(double x, @Default("0") double y) {
        return new Point(x, y);
    }

    public Point resolvePointWithSender(Object sender, double x, double y) {
        return new Point(x, y);
    }

    public Point resolvePointNamed(@Name("ax") double x, @Name("ay") double y) {
        return new Point(x, y);
    }

    public Point resolvePointSuggest(@Suggest("modes") String mode, double x) {
        return new Point(x, mode.hashCode() % 100);
    }

    public int resolveClamped(@Min(0) @Max(100) int value) {
        return value;
    }

    public String resolveGreedy(@Greedy String text) {
        return text;
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
        ((TestSender) sender).sendMessage("Result: " + (num1 + num2));
    }

    // ── Basic Arithmetic Subcommands ──

    @Command(value = "add")
    public void add(Object sender, int num1, int num2) {
        ((TestSender) sender).sendMessage("Result: " + (num1 + num2));
    }

    @Command(value = "sub", aliases = {"subtract"})
    public void subtract(Object sender, int num1, int num2) {
        ((TestSender) sender).sendMessage("Result: " + (num1 - num2));
    }

    @Command(value = "mul", aliases = {"multiply"})
    public void multiply(Object sender, int num1, int num2) {
        ((TestSender) sender).sendMessage("Result: " + (num1 * num2));
    }

    @Command(value = "div", aliases = {"divide"})
    public void divide(Object sender, int num1, @ValidateWith("validateDivider") int num2) {
        ((TestSender) sender).sendMessage("Result: " + ((double) num1 / num2));
    }

    // ── @Suggest Feature Demo ──

    /**
     * Demonstrates field-based @Suggest for operator selection.
     */
    @Command(value = "op")
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
    @Command(value = "mode")
    public void runMode(Object sender, @Suggest("getModes") String mode) {
        ((TestSender) sender).sendMessage("Mode set to: " + mode);
    }

    // ── @Default Feature Demo ──

    /**
     * Demonstrates @Default with default value on String.
     */
    @Command(value = "print")
    public void print(Object sender, @Greedy String text) {
        ((TestSender) sender).sendMessage(text);
    }

    /**
     * Demonstrates @Default with default value on int.
     */
    @Command(value = "repeat")
    public void repeat(Object sender, String text, @Default("1") int count) {
        for (int i = 0; i < count; i++) {
            ((TestSender) sender).sendMessage(text);
        }
    }

    // ── @Greedy Feature Demo ──

    /**
     * Demonstrates @Greedy on String (joins remaining args with spaces).
     */
    @Command(value = "echo")
    public void echo(Object sender, @Greedy String message) {
        ((TestSender) sender).sendMessage(message);
    }

    /**
     * Demonstrates @Greedy on non-String type (joins remaining args, then parses).
     */
    @Command(value = "parse")
    public void parse(Object sender, @Greedy double value) {
        ((TestSender) sender).sendMessage("Parsed: " + value);
    }

    /**
     * Demonstrates @Greedy on array type (creates array from remaining args).
     */
    @Command(value = "sum")
    public void sum(Object sender, @Greedy int[] numbers) {
        int total = 0;
        for (int n : numbers) total += n;
        ((TestSender) sender).sendMessage("Sum: " + total);
    }

    // ── @Name Feature Demo ──

    /**
     * Demonstrates @Name to override parameter name in usage messages.
     */
    @Command(value = "msg")
    public void sendMessage(Object sender, String target, @Name("text") @Greedy String message) {
        ((TestSender) sender).sendMessage("To " + target + ": " + message);
    }

    // ── @Resolve Feature Demo ──

    /**
     * Demonstrates parameter-level @Resolve with named method.
     */
    @Command(value = "point")
    public void runPoint(Object sender, @Resolve("resolvePoint") Point pt) {
        ((TestSender) sender).sendMessage("Point: (" + pt.x + ", " + pt.y + ")");
    }

    /**
     * Demonstrates sender-level @Resolve.
     */
    @Command("whoami")
    public void whoAmI(@Resolve("resolveSender") CustomSender sender) {
        sender.sendMessage("You are: " + sender.getName());
    }

    // ── @ValidateWith Feature Demo ──

    /**
     * Demonstrates @ValidateWith with custom validation method.
     */
    @Command(value = "coord")
    public void setCoordinate(Object sender,
                              @ValidateWith("validateCoordinate") double x,
                              @ValidateWith("validateCoordinate") double y,
                              @ValidateWith("validateCoordinate") double z) {
        ((TestSender) sender).sendMessage("Location set to: (" + x + ", " + y + ", " + z + ")");
    }

    // ── @Min / @Max Feature Demo ──

    /**
     * Demonstrates @Min and @Max validation on numeric types.
     */
    @Command(value = "level")
    public void setLevel(Object sender,
                         @Min(1) @Max(100) int level,
                         @Min(0) @Max(10) double multiplier) {
        ((TestSender) sender).sendMessage("Level " + level + " with multiplier " + multiplier);
    }

    // ── Enum Parameter Demo ──

    /**
     * Demonstrates enum parameter resolution (via registerProvider in CLIApp).
     */
    @Command("enumop")
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

    // ── Multi-Param Combination Demos ──

    @Command("resolveAndSuggest")
    public void resolveAndSuggest(Object sender, @Resolve("resolvePoint") Point pt, @Suggest("modes") String mode) {
        ((TestSender) sender).sendMessage("resolveAndSuggest=" + pt.x + "," + pt.y + "," + mode);
    }

    @Command("resolveAndDefault")
    public void resolveAndDefault(Object sender, @Resolve("resolvePoint") Point pt, @Default("normal") String mode) {
        ((TestSender) sender).sendMessage("resolveAndDefault=" + pt.x + "," + pt.y + "," + mode);
    }

    @Command("resolveAndValidate")
    public void resolveAndValidate(Object sender, @Resolve("resolvePoint") Point pt, @Min(0) @Max(100) @Default("50") int level) {
        ((TestSender) sender).sendMessage("resolveAndValidate=" + pt.x + "," + pt.y + "," + level);
    }

    @Command("suggestAndDefault")
    public void suggestAndDefault(Object sender, @Suggest("modes") String mode, @Default("50") int count) {
        ((TestSender) sender).sendMessage("suggestAndDefault=" + mode + "," + count);
    }

    @Command("defaultAndGreedy")
    public void defaultAndGreedy(Object sender, @Default("hello") String greeting, @Greedy String text) {
        ((TestSender) sender).sendMessage("defaultAndGreedy=" + greeting + "," + text);
    }

    // ── Resolver Combination Demos ──

    @Command("sameSender")
    public void sameSender(Object sender, @Resolve("resolvePointWithSender") Point pt) {
        ((TestSender) sender).sendMessage("sameSender=" + pt.x + "," + pt.y);
    }

    @Command("namedResolver")
    public void namedResolver(Object sender, @Resolve("resolvePointNamed") Point pt) {
        ((TestSender) sender).sendMessage("namedResolver=" + pt.x + "," + pt.y);
    }

    @Command("suggestResolver")
    public void suggestResolver(Object sender, @Resolve("resolvePointSuggest") Point pt) {
        ((TestSender) sender).sendMessage("suggestResolver=" + pt.x + "," + pt.y);
    }

    @Command("clamped")
    public void clamped(Object sender, @Resolve("resolveClamped") int value) {
        ((TestSender) sender).sendMessage("clamped=" + value);
    }

    @Command("greedyResolve")
    public void greedyResolve(Object sender, @Resolve("resolveGreedy") String text) {
        ((TestSender) sender).sendMessage("greedyResolve=" + text);
    }

    @Command("greedyResolveWithPrefix")
    public void greedyResolveWithPrefix(Object sender, String prefix, @Resolve("resolveGreedy") String text) {
        ((TestSender) sender).sendMessage("greedyResolveWithPrefix=" + prefix + ":" + text);
    }

    // ── Nested Subcommand Class ──

    public enum MathOp {
        ADD, SUBTRACT, MULTIPLY, DIVIDE
    }

    // ── Inner Types ──

    public static class CustomSender {
        private final String name;
        private final Object delegate;
        private final java.util.List<String> messages = new java.util.ArrayList<>();

        public CustomSender(String name) {
            this(name, null);
        }

        public CustomSender(String name, Object delegate) {
            this.name = name;
            this.delegate = delegate;
        }

        public String getName() {
            return name;
        }

        public void sendMessage(String msg) {
            messages.add(msg);
            if (delegate instanceof TestSender) {
                ((TestSender) delegate).sendMessage(msg);
            }
        }

        public java.util.List<String> getMessages() {
            return messages;
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
    @Command("advanced")
    public class AdvancedCommands {
        @Default
        public void execute(Object sender) {
            ((TestSender) sender).sendMessage("Advanced operations: power, sqrt");
        }

        @Command("power")
        public void power(Object sender, double base, double exponent) {
            ((TestSender) sender).sendMessage("Result: " + Math.pow(base, exponent));
        }

        @Command("sqrt")
        public void sqrt(Object sender, @Min(0) double number) {
            ((TestSender) sender).sendMessage("Result: " + Math.sqrt(number));
        }

        /**
         * Demonstrates nested class with its own @Default parameters.
         */
        @Command("log")
        public void logarithm(Object sender, double value, @Default("10") double base) {
            ((TestSender) sender).sendMessage("Result: " + (Math.log(value) / Math.log(base)));
        }
    }

    /**
     * Nested subcommand class demonstrating {@link Resolve} referencing
     * a resolver method defined in the outer class.
     * <p>
     * This verifies the fix for NPE when {@code @Resolve} references an outer
     * class method from a {@code @Command} inner class.
     * <p>
     * Usage: /calc resolve 10 20, /calc resolve display 5 6
     */
    @Command("resolve")
    public class ResolveCommands {
        @Default
        public void execute(Object sender, @Resolve("resolvePoint") Point pt) {
            ((TestSender) sender).sendMessage("Resolved point: (" + pt.x + ", " + pt.y + ")");
        }

        @Command("display")
        public void display(Object sender, @Resolve("resolvePoint") Point pt) {
            ((TestSender) sender).sendMessage("Displaying point: (" + pt.x + ", " + pt.y + ")");
        }
    }
}
