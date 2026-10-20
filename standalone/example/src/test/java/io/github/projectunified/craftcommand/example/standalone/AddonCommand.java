package io.github.projectunified.craftcommand.example.standalone;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Default;

@Command("addon")
public class AddonCommand {
    @Default
    public void run(Object sender) {
    }
}
