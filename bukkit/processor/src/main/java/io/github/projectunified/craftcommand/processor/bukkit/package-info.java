/**
 * Bukkit-specific annotation processor for CraftCommand.
 *
 * <p>Generates {@code *$BukkitCommand.java} wrapper classes for Bukkit/Spigot servers.
 *
 * <p>{@code PermissionPrism} gives this processor typed, mirror-based access to
 * {@link io.github.projectunified.craftcommand.bukkit.annotation.Permission}.
 */
@GeneratePrism(value = Permission.class, publicAccess = true)
package io.github.projectunified.craftcommand.processor.bukkit;

import io.avaje.prism.GeneratePrism;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
