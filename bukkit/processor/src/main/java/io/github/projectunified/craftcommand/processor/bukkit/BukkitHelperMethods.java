package io.github.projectunified.craftcommand.processor.bukkit;

import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.exception.CommandException;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.lang.model.element.Modifier;
import java.util.List;

/**
 * Generates the Bukkit helper methods used by the wrappers: player/world lookup and suggestions.
 */
public final class BukkitHelperMethods {

    private static final ClassName BUKKIT = ClassName.get("org.bukkit", "Bukkit");
    private static final ClassName PLAYER = ClassName.get("org.bukkit.entity", "Player");
    private static final ClassName OFFLINE_PLAYER = ClassName.get("org.bukkit", "OfflinePlayer");
    private static final ClassName WORLD = ClassName.get("org.bukkit", "World");
    private static final ClassName LOCATION = ClassName.get("org.bukkit", "Location");
    private static final ClassName ARRAY_LIST = ClassName.get("java.util", "ArrayList");
    private static final ClassName STRING = ClassName.get(String.class);
    private static final ParameterizedTypeName STRING_LIST =
            ParameterizedTypeName.get(ClassName.get(List.class), STRING);
    private static final ClassName NUMBER_FORMAT_EX = ClassName.get(NumberFormatException.class);
    private static final ClassName COMMAND_MANAGER = ClassName.get("io.github.projectunified.craftcommand", "CommandManager");

    private BukkitHelperMethods() {
    }

    /**
     * Generate all needed helpers into the given TypeSpec, based on which types the command uses.
     */
    public static void generate(TypeSpec.Builder typeSpec, CommandModel model) {
        boolean hasPlayer = hasParameterType(model, PLAYER);
        boolean hasOfflinePlayer = hasParameterType(model, OFFLINE_PLAYER);
        boolean hasWorld = hasParameterType(model, WORLD);
        boolean hasLocation = hasParameterType(model, LOCATION);

        if (hasPlayer || hasOfflinePlayer) {
            typeSpec.addMethod(suggestPlayers());
        }
        if (hasWorld) {
            typeSpec.addMethod(suggestWorlds());
        }
        if (hasPlayer) {
            typeSpec.addMethod(getPlayer());
        }
        if (hasOfflinePlayer) {
            typeSpec.addMethod(getOfflinePlayer());
        }
        if (hasWorld) {
            typeSpec.addMethod(getWorld());
        }
        if (hasLocation) {
            typeSpec.addMethod(getLocation());
        }
    }

    /**
     * Check if any method in the command tree has a parameter of the given type name.
     */
    private static boolean hasParameterType(CommandModel model, TypeName typeName) {
        if (model.getDefaultMethod() != null && hasParameterType(model.getDefaultMethod(), typeName)) return true;
        for (MethodModel sub : model.getSubcommands()) {
            if (hasParameterType(sub, typeName)) return true;
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            if (hasParameterType(child, typeName)) return true;
        }
        return false;
    }

    private static boolean hasParameterType(MethodModel method, TypeName typeName) {
        // Includes resolver sender parameters on purpose: over-generating a helper is safe, missing one is not.
        for (ParameterModel p : method.parameters()) {
            if (TypeName.get(p.type()).equals(typeName)) return true;
            MethodModel resolver = p.resolverMethod();
            if (resolver != null && hasParameterType(resolver, typeName)) return true;
        }
        return false;
    }

    private static MethodSpec suggestPlayers() {
        return MethodSpec.methodBuilder("suggestPlayers")
                .addModifiers(Modifier.PRIVATE)
                .returns(STRING_LIST)
                .addParameter(STRING, "current")
                .addStatement("$T list = new $T<>()", STRING_LIST, ARRAY_LIST)
                .beginControlFlow("for ($T onlinePlayer : $T.getOnlinePlayers())", PLAYER, BUKKIT)
                .addStatement("list.add(onlinePlayer.getName())")
                .endControlFlow()
                .addStatement("return $T.filterSuggestions(list, current)", COMMAND_MANAGER)
                .build();
    }

    private static MethodSpec suggestWorlds() {
        return MethodSpec.methodBuilder("suggestWorlds")
                .addModifiers(Modifier.PRIVATE)
                .returns(STRING_LIST)
                .addParameter(STRING, "current")
                .addStatement("$T list = new $T<>()", STRING_LIST, ARRAY_LIST)
                .beginControlFlow("for ($T world : $T.getWorlds())", WORLD, BUKKIT)
                .addStatement("list.add(world.getName())")
                .endControlFlow()
                .addStatement("return $T.filterSuggestions(list, current)", COMMAND_MANAGER)
                .build();
    }

    private static MethodSpec getPlayer() {
        return lookup("getPlayer", PLAYER, "getPlayer", "player-not-found", "Player not found: %s");
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
        return lookup("getWorld", WORLD, "getWorld", "world-not-found", "World not found: %s");
    }

    private static MethodSpec lookup(String methodName, ClassName type, String bukkitMethod,
                                     String errorKey, String errorMessage) {
        return MethodSpec.methodBuilder(methodName)
                .addModifiers(Modifier.PRIVATE)
                .returns(type)
                .addParameter(STRING, "name")
                .beginControlFlow("if (name == null)")
                .addStatement("return null")
                .endControlFlow()
                .addStatement("$T result = $T.$L(name)", type, BUKKIT, bukkitMethod)
                .beginControlFlow("if (result == null)")
                .addStatement("throw new $T(manager.formatMessage($S, $S, name))",
                        CommandException.class, errorKey, errorMessage)
                .endControlFlow()
                .addStatement("return result")
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
