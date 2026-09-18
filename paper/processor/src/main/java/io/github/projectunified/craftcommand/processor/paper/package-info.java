/**
 * Paper-specific annotation processor for CraftCommand.
 *
 * <p>Generates {@code *$PaperCommand.java} wrapper classes
 * with Brigadier integration for Paper servers.
 *
 * <p>{@code PermissionPrism} gives this processor typed, mirror-based access to
 * {@link io.github.projectunified.craftcommand.bukkit.annotation.Permission}.
 */
@GeneratePrism(value = Permission.class, publicAccess = true)
package io.github.projectunified.craftcommand.processor.paper;

import io.avaje.prism.GeneratePrism;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
