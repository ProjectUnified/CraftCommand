package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Default;
import io.github.projectunified.craftcommand.annotation.Resolve;
import io.github.projectunified.craftcommand.validation.annotation.ValidateWith;

/**
 * Covers Resolve combinations:
 * named, sender-level, Default, Min, ValidateWith, implicit by return type, nested.
 */
@Command(value = "resolv", description = "Resolve annotation combinations")
public class ResolveCommand {

    public Point resolvePoint(double x, double y) {
        return new Point(x, y);
    }

    public Point resolvePointDefault(double x, @Default("0") double y) {
        return new Point(x, y);
    }

    public CustomSender resolveSender(Object sender) {
        return new CustomSender(sender.toString());
    }

    @Resolve
    public Point resolveImplicit(double x, double y) {
        return new Point(x, y);
    }

    public Point resolveWithValidator(double x, double y) {
        return new Point(x, y);
    }

    public void validatePoint(Point pt) {
        if (pt.x < 0 || pt.y < 0) {
            throw new IllegalArgumentException("Point coordinates must be non-negative!");
        }
    }

    @Command("named")
    public void namedResolve(Object sender, @Resolve("resolvePoint") Point pt) {
        ((TestSender) sender).sendMessage("named=" + pt.x + "," + pt.y);
    }

    @Command("sender")
    public void senderResolve(@Resolve("resolveSender") CustomSender sender) {
        // CustomSender wraps the original sender; we can't access TestSender messages here
        // This tests that @Resolve on the sender parameter works correctly
    }

    @Command("def")
    public void resolveDefault(Object sender, @Resolve("resolvePointDefault") Point pt) {
        ((TestSender) sender).sendMessage("def=" + pt.x + "," + pt.y);
    }

    @Command("min")
    public void resolveMin(Object sender, @Resolve("resolvePoint") Point pt) {
        ((TestSender) sender).sendMessage("min=" + pt.x + "," + pt.y);
    }

    @Command("vw")
    public void resolveVw(Object sender, @ValidateWith("validatePoint") @Resolve("resolveWithValidator") Point pt) {
        ((TestSender) sender).sendMessage("vw=" + pt.x + "," + pt.y);
    }

    @Command("implicit")
    public void implicitResolve(Object sender, @Resolve("resolveImplicit") Point pt) {
        ((TestSender) sender).sendMessage("implicit=" + pt.x + "," + pt.y);
    }

    public static class CustomSender {
        public final String name;

        public CustomSender(String name) {
            this.name = name;
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
}
