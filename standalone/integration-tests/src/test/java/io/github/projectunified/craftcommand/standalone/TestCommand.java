package io.github.projectunified.craftcommand.standalone;

import io.github.projectunified.craftcommand.annotation.*;
import io.github.projectunified.craftcommand.validation.annotation.Max;
import io.github.projectunified.craftcommand.validation.annotation.Min;
import io.github.projectunified.craftcommand.validation.annotation.ValidateWith;

import java.util.Arrays;
import java.util.List;

@Command(value = "test", aliases = {"t"}, description = "Test Command")
public class TestCommand {
    public final List<String> modes = Arrays.asList("easy", "hard");

    public List<String> getItems(Object sender, String[] args, String current) {
        return Arrays.asList("sword", "shield", "potion");
    }

    // Custom Validation Method
    public void validateStringLength(String input) {
        if (input.length() > 5) {
            throw new IllegalArgumentException("Input is too long");
        }
    }

    // Custom local resolver method referenced by parameter-level @Resolve
    public CustomImpl resolveCustomParameter(String current) {
        return new CustomImpl("resolved_" + current);
    }

    // Custom resolver for first parameter (sender)
    public CustomSender resolveSender(Object sender) {
        return new CustomSender(sender.toString() + "_resolved");
    }

    @Default
    public void execute(Object sender, @Suggest("getItems") String item, @Optional("1") int amount) {
        // Do nothing
    }

    @Subcommand("mode")
    public void setMode(Object sender, @Suggest("modes") String mode) {
        // Do nothing
    }

    @Subcommand("greedy")
    public void runGreedy(Object sender, @Greedy String text) {
        // Do nothing
    }

    @Subcommand("enum")
    public void runEnum(Object sender, TestEnum val) {
        // Do nothing
    }

    // Implicit resolver via @Resolve on method matching type CustomImpl
    @Resolve
    public CustomImpl resolveCustom(String current) {
        return new CustomImpl("local_" + current);
    }

    @Subcommand("custom")
    public void runCustom(Object sender, CustomImpl custom) {
        if (!custom.getName().equals("local_hello")) {
            throw new IllegalStateException("Must use local resolver!");
        }
    }

    // Parameter resolver mapping via name
    @Subcommand("param-resolve")
    public void runParamResolve(Object sender, @Resolve("resolveCustomParameter") CustomImpl custom) {
        if (!custom.getName().equals("resolved_hello")) {
            throw new IllegalStateException("Must use resolved parameter by name!");
        }
    }

    // Custom resolved first parameter (sender)
    @Subcommand("sender-resolve")
    public void runSenderResolve(@Resolve("resolveSender") CustomSender sender) {
        if (!sender.getName().equals("mySender_resolved")) {
            throw new IllegalStateException("Must resolve first parameter (sender)!");
        }
    }

    // Parameter validations: @Min, @Max, and @ValidateWith
    @Subcommand("validate")
    public void runValidate(Object sender,
                            @Min(5) @Max(10) int range,
                            @ValidateWith("validateStringLength") String text) {
        // Do nothing
    }

    // Parameter validations with custom messages
    @Subcommand("validate-msg")
    public void runValidateMsg(Object sender,
                               @Min(value = 5, message = "Value is too small!") int val,
                               @Max(value = 10, message = "error.range.max") int val2) {
        // Do nothing
    }

    @Subcommand("point")
    public void runPoint(Object sender, Point pt, String msg) {
        // Do nothing
    }

    public Point resolvePointLocal(double x, @Optional("0") double y) {
        return new Point(x, y);
    }

    @Subcommand("point-local")
    public void runPointLocal(Object sender, @Resolve("resolvePointLocal") Point pt) {
        // Do nothing
    }

    @Subcommand("custom-sender")
    public void runCustomSender(CustomSender sender) {
        // Do nothing
    }

    @Subcommand("primitives")
    public void runPrimitives(Object sender, long l, short s, byte b, char c) {
        if (l != 100L || s != (short) 5 || b != (byte) 2 || c != 'A') {
            throw new IllegalArgumentException("Incorrect primitive values!");
        }
    }

    public enum TestEnum {
        FIRST, SECOND
    }

    public interface CustomInterface {
        String getName();
    }

    public static class CustomImpl implements CustomInterface {
        private final String name;

        public CustomImpl(String name) {
            this.name = name;
        }

        @Override
        public String getName() {
            return name;
        }
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

    @Subcommand("nested")
    public class NestedSub {
        @Default
        public void execute(Object sender, String arg) {
            // Do nothing
        }

        @Subcommand("sub")
        public void runSub(Object sender, int val) {
            // Do nothing
        }

        // Subcommands inside a subcommand class (Deep nesting: Root -> Sub -> SubSub -> SubSubSub)
        @Subcommand("deep")
        public class DeepSub {
            @Default
            public void execute(Object sender) {
                // Do nothing
            }

            @Subcommand("value")
            public void runDeepValue(Object sender, int val) {
                // Do nothing
            }
        }
    }

    @Subcommand("nested-inherit")
    public class NestedInherit {
        @Default
        public void execute(Object sender, CustomImpl custom) {
            if (!custom.getName().equals("local_hello")) {
                throw new IllegalStateException("Must inherit parent resolver!");
            }
        }
    }

    @Subcommand("nested-override")
    public class NestedOverride {
        @Resolve
        public CustomImpl resolveCustomOverride(String current) {
            return new CustomImpl("override_" + current);
        }

        @Default
        public void execute(Object sender, CustomImpl custom) {
            if (!custom.getName().equals("override_hello")) {
                throw new IllegalStateException("Must use overridden resolver!");
            }
        }
    }
}
