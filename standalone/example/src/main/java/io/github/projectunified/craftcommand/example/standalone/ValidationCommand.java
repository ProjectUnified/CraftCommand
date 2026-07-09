package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Default;
import io.github.projectunified.craftcommand.validation.annotation.Max;
import io.github.projectunified.craftcommand.validation.annotation.Min;
import io.github.projectunified.craftcommand.validation.annotation.ValidateWith;

/**
 * Covers validation annotation combinations:
 * Min alone, Max alone, Min+Max, custom messages, Min+ValidateWith stack,
 * Default+Min, Default+Max, Default+ValidateWith.
 */
@Command(value = "validate", description = "Validation annotation combinations")
public class ValidationCommand {

    public void validatePositive(Object sender, int value) {
        if (value < 0) {
            throw new IllegalArgumentException("Must be non-negative!");
        }
    }

    public void validateRange(Object sender, int value) {
        if (value < 1 || value > 50) {
            throw new IllegalArgumentException("Must be between 1 and 50!");
        }
    }

    @Command("min")
    public void minOnly(Object sender, @Min(0) int value) {
        ((TestSender) sender).sendMessage("min=" + value);
    }

    @Command("max")
    public void maxOnly(Object sender, @Max(100) int value) {
        ((TestSender) sender).sendMessage("max=" + value);
    }

    @Command("minmax")
    public void minMax(Object sender, @Min(0) @Max(100) int value) {
        ((TestSender) sender).sendMessage("minmax=" + value);
    }

    @Command("minmsg")
    public void minCustomMessage(Object sender,
                                 @Min(value = 0, message = "%1$s must be >= %2$s") int value) {
        ((TestSender) sender).sendMessage("minmsg=" + value);
    }

    @Command("maxmsg")
    public void maxCustomMessage(Object sender,
                                 @Max(value = 100, message = "%1$s must be <= %2$s") int value) {
        ((TestSender) sender).sendMessage("maxmsg=" + value);
    }

    @Command("vwmsg")
    public void vwCustomMessage(Object sender,
                                @ValidateWith(value = "validatePositive", message = "%2$s") int value) {
        ((TestSender) sender).sendMessage("vwmsg=" + value);
    }

    @Command("minvw")
    public void minVwStack(Object sender,
                           @Min(0) @ValidateWith("validatePositive") int value) {
        ((TestSender) sender).sendMessage("minvw=" + value);
    }

    @Command("defmin")
    public void defaultMin(Object sender, @Min(0) @Default("50") int value) {
        ((TestSender) sender).sendMessage("defmin=" + value);
    }

    @Command("defmax")
    public void defaultMax(Object sender, @Max(100) @Default("50") int value) {
        ((TestSender) sender).sendMessage("defmax=" + value);
    }

    @Command("defvw")
    public void defaultVw(Object sender,
                          @ValidateWith("validateRange") @Default("25") int value) {
        ((TestSender) sender).sendMessage("defvw=" + value);
    }
}
