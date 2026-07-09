package io.github.projectunified.craftcommand.processor.bukkit;

import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.lang.model.element.Modifier;
import java.util.List;

/**
 * Shared generator for Bukkit/Paper-Basic helper methods emitted into generated
 * command wrappers: {@code getPlayer}, {@code getOfflinePlayer}, {@code getWorld},
 * {@code getLocation}, {@code suggestPlayers}, {@code suggestWorlds}.
 *
 * <p>Previously these ~150 lines were copy-pasted identically between
 * {@link BukkitCommandProcessor}. Now both
 * delegate here.
 */
public final class BukkitHelperMethods {

    private static final ClassName BUKKIT = ClassName.get("org.bukkit", "Bukkit");
    private static final ClassName PLAYER = ClassName.get("org.bukkit.entity", "Player");
    private static final ClassName OFFLINE_PLAYER = ClassName.get("org.bukkit", "OfflinePlayer");
    private static final ClassName WORLD = ClassName.get("org.bukkit", "World");
    private static final ClassName LOCATION = ClassName.get("org.bukkit", "Location");
    private static final ClassName ARRAY_LIST = ClassName.get("java.util", "ArrayList");
    private static final ClassName LIST = ClassName.get(List.class);
    private static final ClassName STRING = ClassName.get(String.class);
    private static final ClassName NUMBER_FORMAT_EX = ClassName.get(NumberFormatException.class);

    private BukkitHelperMethods() {
    }

    /**
     * Generate all needed helpers into the given TypeSpec, based on which types the command uses.
     */
    public static void generate(TypeSpec.Builder typeSpec, CommandModel model) {
        boolean hasPlayer = hasParameterType(model, "org.bukkit.entity.Player");
        boolean hasOfflinePlayer = hasParameterType(model, "org.bukkit.OfflinePlayer");
        boolean hasWorld = hasParameterType(model, "org.bukkit.World");
        boolean hasLocation = hasParameterType(model, "org.bukkit.Location");

        if (hasPlayer || hasOfflinePlayer) {
            typeSpec.addMethod(suggestPlayers());
        }
        if (hasWorld || hasLocation) {
            typeSpec.addMethod(suggestWorlds());
        }
        if (hasPlayer) {
            typeSpec.addMethod(getPlayer());
        }
        if (hasOfflinePlayer) {
            typeSpec.addMethod(getOfflinePlayer());
        }
        if (hasWorld || hasLocation) {
            typeSpec.addMethod(getWorld());
        }
        if (hasLocation) {
            typeSpec.addMethod(getLocation());
        }
    }

    /**
     * Check if any method in the command tree has a parameter of the given type name.
     */
    public static boolean hasParameterType(CommandModel model, String typeName) {
        if (model.getDefaultMethod() != null && hasParameterType(model.getDefaultMethod(), typeName)) return true;
        for (MethodModel sub : model.getSubcommands()) {
            if (hasParameterType(sub, typeName)) return true;
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            if (hasParameterType(child, typeName)) return true;
        }
        return false;
    }

    private static boolean hasParameterType(MethodModel method, String typeName) {
        for (ParameterModel p : method.getParameters()) {
            if (TypeName.get(p.getType()).toString().equals(typeName)) return true;
        }
        return false;
    }

    private static MethodSpec suggestPlayers() {
        return MethodSpec.methodBuilder("suggestPlayers")
                .addModifiers(Modifier.PRIVATE)
                .returns(ParameterizedTypeName.get(LIST, STRING))
                .addParameter(STRING, "current")
                .addStatement("$T list = new $T<>($T.getOnlinePlayers().size())", LIST, ARRAY_LIST, BUKKIT)
                .addStatement("String lower = current.toLowerCase()")
                .beginControlFlow("for ($T onlinePlayer : $T.getOnlinePlayers())", PLAYER, BUKKIT)
                .beginControlFlow("if (onlinePlayer.getName().toLowerCase().startsWith(lower))")
                .addStatement("list.add(onlinePlayer.getName())")
                .endControlFlow()
                .endControlFlow()
                .addStatement("return list")
                .build();
    }

    private static MethodSpec suggestWorlds() {
        return MethodSpec.methodBuilder("suggestWorlds")
                .addModifiers(Modifier.PRIVATE)
                .returns(ParameterizedTypeName.get(LIST, STRING))
                .addParameter(STRING, "current")
                .addStatement("$T list = new $T<>($T.getWorlds().size())", LIST, ARRAY_LIST, BUKKIT)
                .addStatement("String lower = current.toLowerCase()")
                .beginControlFlow("for ($T world : $T.getWorlds())", WORLD, BUKKIT)
                .beginControlFlow("if (world.getName().toLowerCase().startsWith(lower))")
                .addStatement("list.add(world.getName())")
                .endControlFlow()
                .endControlFlow()
                .addStatement("return list")
                .build();
    }

    private static MethodSpec getPlayer() {
        return MethodSpec.methodBuilder("getPlayer")
                .addModifiers(Modifier.PRIVATE)
                .returns(PLAYER)
                .addParameter(STRING, "name")
                .beginControlFlow("if (name == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("$T tempPlayer = $T.getPlayer(name)", PLAYER, BUKKIT)
                .beginControlFlow("if (tempPlayer == null)")
                .addStatement("throw new $T(manager.formatMessage($S, $S, name))", CommandException.class, "player-not-found", "Player not found: %s")
                .endControlFlow()
                .addStatement("return tempPlayer")
                .build();
    }

    private static MethodSpec getOfflinePlayer() {
        return MethodSpec.methodBuilder("getOfflinePlayer")
                .addModifiers(Modifier.PRIVATE)
                .returns(OFFLINE_PLAYER)
                .addParameter(STRING, "name")
                .beginControlFlow("if (name == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("$T tempOffline = $T.getOfflinePlayer(name)", OFFLINE_PLAYER, BUKKIT)
                .beginControlFlow("if (!tempOffline.hasPlayedBefore())")
                .addStatement("throw new $T(manager.formatMessage($S, $S, name))", CommandException.class, "player-not-found", "Player not found: %s")
                .endControlFlow()
                .addStatement("return tempOffline")
                .build();
    }

    private static MethodSpec getWorld() {
        return MethodSpec.methodBuilder("getWorld")
                .addModifiers(Modifier.PRIVATE)
                .returns(WORLD)
                .addParameter(STRING, "name")
                .beginControlFlow("if (name == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("$T tempWorld = $T.getWorld(name)", WORLD, BUKKIT)
                .beginControlFlow("if (tempWorld == null)")
                .addStatement("throw new $T(manager.formatMessage($S, $S, name))", CommandException.class, "world-not-found", "World not found: %s")
                .endControlFlow()
                .addStatement("return tempWorld")
                .build();
    }

    private static MethodSpec getLocation() {
        ClassName senderClass = ClassName.get("org.bukkit.command", "CommandSender");
        return MethodSpec.methodBuilder("getLocation")
                .addModifiers(Modifier.PRIVATE)
                .returns(LOCATION)
                .addParameter(String[].class, "args")
                .addParameter(int.class, "startIdx")
                .addParameter(senderClass, "sender")
                .beginControlFlow("try")
                .addStatement("double x = $T.parseDouble(args[startIdx])", Double.class)
                .addStatement("double y = $T.parseDouble(args[startIdx + 1])", Double.class)
                .addStatement("double z = $T.parseDouble(args[startIdx + 2])", Double.class)
                .addStatement("return new $T(sender instanceof $T ? (($T) sender).getWorld() : null, x, y, z)", LOCATION, PLAYER, PLAYER)
                .nextControlFlow("catch ($T e)", NUMBER_FORMAT_EX)
                .addStatement("throw new $T(manager.formatMessage($S, $S))", CommandException.class, "invalid-coordinate", "Invalid coordinate format; must be numeric values.")
                .endControlFlow()
                .build();
    }
}
