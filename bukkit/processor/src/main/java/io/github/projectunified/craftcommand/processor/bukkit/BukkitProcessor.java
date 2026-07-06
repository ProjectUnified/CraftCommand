package io.github.projectunified.craftcommand.processor.bukkit;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.github.projectunified.craftcommand.processor.AbstractCommandProcessor;
import io.github.projectunified.craftcommand.processor.model.CommandModel;

import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.Collections;
import java.util.List;

/**
 * Annotation processor for Bukkit platforms.
 * Generates custom Bukkit wrapper command executors extending {@code org.bukkit.command.Command}.
 */
@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.projectunified.craftcommand.annotation.Command")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class BukkitProcessor extends AbstractCommandProcessor {

    private final ClassName commandSenderClass = ClassName.get("org.bukkit.command", "CommandSender");
    private final ClassName chatColorClass = ClassName.get("org.bukkit", "ChatColor");

    @Override
    protected String getWrapperClassSuffix() {
        return "_Executor";
    }

    @Override
    protected void configureSuperType(TypeSpec.Builder typeSpec) {
        typeSpec.superclass(ClassName.get("org.bukkit.command", "Command"));
    }

    @Override
    protected ClassName getSenderTypeName() {
        return commandSenderClass;
    }

    @Override
    protected TypeName getManagerType() {
        ClassName commandManagerClass = ClassName.get("io.github.projectunified.craftcommand", "CommandManager");
        return ParameterizedTypeName.get(commandManagerClass, commandSenderClass);
    }

    @Override
    protected boolean isSenderBaseType(TypeName typeName) {
        String name = typeName.toString();
        return name.equals("java.lang.Object") || name.equals("org.bukkit.command.CommandSender");
    }

    @Override
    protected void configureConstructor(MethodSpec.Builder constructorBuilder, CommandModel model) {
        constructorBuilder.addStatement("super($S)", model.getCommandName());
        constructorBuilder.addStatement("this.setDescription($S)", model.getDescription());
        constructorBuilder.addStatement("this.setAliases($L)", buildAliasesExpression(model));
    }

    /**
     * Generates Bukkit-specific wrapper entry methods.
     * This overrides org.bukkit.command.Command's execute and tabComplete methods.
     */
    @Override
    protected void generateEntryMethods(TypeSpec.Builder typeSpec, CommandModel model, TypeElement typeElement) {
        // execute(CommandSender sender, String label, String[] args)
        MethodSpec.Builder executeSpec = MethodSpec.methodBuilder("execute")
                .addJavadoc("Executes the Bukkit command.\n\n"
                        + "@param sender the execution initiator\n"
                        + "@param label the command label used\n"
                        + "@param args command arguments\n"
                        + "@return true if executed successfully\n")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(commandSenderClass, "sender")
                .addParameter(String.class, "label")
                .addParameter(String[].class, "args");

        generateExecuteMethodBody(executeSpec, model, "return true");
        typeSpec.addMethod(executeSpec.build());

        // tabComplete(CommandSender sender, String alias, String[] args)
        MethodSpec.Builder tabSpec = MethodSpec.methodBuilder("tabComplete")
                .addJavadoc("Provides suggestions for Bukkit tab completion.\n\n"
                        + "@param sender the execution initiator\n"
                        + "@param alias the command alias used\n"
                        + "@param args command arguments\n"
                        + "@return suggestions list\n")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addParameter(commandSenderClass, "sender")
                .addParameter(String.class, "alias")
                .addParameter(String[].class, "args")
                .addException(IllegalArgumentException.class);

        generateSuggestionRouting(tabSpec, model, "args", "instance", model);
        typeSpec.addMethod(tabSpec.build());
    }

    @Override
    protected void generateUnknownSubcommandMessage(MethodSpec.Builder methodSpec, CommandModel model) {
        methodSpec.addStatement("sender.sendMessage($T.RED + $S)", chatColorClass, "Unknown subcommand. Available: " + getSubcommandNames(model));
    }

    @Override
    protected void onBeforeExecute(MethodSpec.Builder methodSpec, javax.lang.model.element.Element element, String returnStatement) {
        Permission permission = element.getAnnotation(Permission.class);
        if (permission != null) {
            generatePermissionCheck(methodSpec, permission, returnStatement);
        }
    }

    private void generatePermissionCheck(MethodSpec.Builder methodSpec, Permission permission, String returnStatement) {
        methodSpec.addComment("Verify execution permission: '" + permission.value() + "'");
        methodSpec.beginControlFlow("if (!sender.hasPermission($S))", permission.value());

        String messageKey = permission.message().isEmpty() ? "permission" : permission.message();
        String defaultTemplate = permission.message().isEmpty()
                ? "You do not have permission to execute this command."
                : permission.message();

        if (permission.message().isEmpty()) {
            methodSpec.addStatement("sender.sendMessage($T.RED + manager.formatMessage($S, $S, $S))",
                    chatColorClass, messageKey, defaultTemplate, permission.value());
        } else {
            methodSpec.addStatement("sender.sendMessage(manager.formatMessage($S, $S, $S))",
                    messageKey, defaultTemplate, permission.value());
        }

        methodSpec.addStatement(returnStatement)
                .endControlFlow();
    }

    @Override
    protected void onSuggestionAdd(MethodSpec.Builder methodSpec, javax.lang.model.element.Element element, Runnable addSuggestions) {
        Permission permission = element.getAnnotation(Permission.class);
        if (permission != null) {
            methodSpec.addComment("Verify permission '" + permission.value() + "' before suggesting");
            methodSpec.beginControlFlow("if (sender.hasPermission($S))", permission.value());
            addSuggestions.run();
            methodSpec.endControlFlow();
        } else {
            addSuggestions.run();
        }
    }

    @Override
    protected void onBeforeSuggest(MethodSpec.Builder methodSpec, javax.lang.model.element.Element element) {
        Permission permission = element.getAnnotation(Permission.class);
        if (permission != null) {
            methodSpec.addComment("Verify suggestion permission: '" + permission.value() + "'");
            methodSpec.beginControlFlow("if (!sender.hasPermission($S))", permission.value())
                    .addStatement("return $T.emptyList()", Collections.class)
                    .endControlFlow();
        }
    }

    @Override
    protected boolean isPlatformBuiltInType(TypeName typeName) {
        String name = typeName.toString();
        return name.equals("org.bukkit.entity.Player")
                || name.equals("org.bukkit.OfflinePlayer")
                || name.equals("org.bukkit.World")
                || name.equals("org.bukkit.Location");
    }

    @Override
    protected int getPlatformBuiltInWidth(TypeName typeName) {
        if (typeName.toString().equals("org.bukkit.Location")) {
            return 4;
        }
        return 1;
    }

    @Override
    protected void generatePlatformInlineResolve(MethodSpec.Builder methodSpec, TypeName typeName, String varName, String argStrVar) {
        String name = typeName.toString();
        ClassName bukkitClass = ClassName.get("org.bukkit", "Bukkit");
        if (name.equals("org.bukkit.entity.Player")) {
            ClassName playerClass = ClassName.get("org.bukkit.entity", "Player");
            methodSpec.addStatement("$T tempPlayer = $T.getPlayer($L)", playerClass, bukkitClass, argStrVar)
                    .beginControlFlow("if (tempPlayer == null)")
                    .addStatement("throw new $T($S + $L)", IllegalArgumentException.class, "Player not found: ", argStrVar)
                    .endControlFlow()
                    .addStatement("$L = tempPlayer", varName);
        } else if (name.equals("org.bukkit.OfflinePlayer")) {
            ClassName offlinePlayerClass = ClassName.get("org.bukkit", "OfflinePlayer");
            methodSpec.addStatement("$T tempOffline = $T.getOfflinePlayer($L)", offlinePlayerClass, bukkitClass, argStrVar)
                    .beginControlFlow("if (!tempOffline.hasPlayedBefore())")
                    .addStatement("throw new $T($S + $L)", IllegalArgumentException.class, "Player not found: ", argStrVar)
                    .endControlFlow()
                    .addStatement("$L = tempOffline", varName);
        } else if (name.equals("org.bukkit.World")) {
            ClassName worldClass = ClassName.get("org.bukkit", "World");
            methodSpec.addStatement("$T tempWorld = $T.getWorld($L)", worldClass, bukkitClass, argStrVar)
                    .beginControlFlow("if (tempWorld == null)")
                    .addStatement("throw new $T($S + $L)", IllegalArgumentException.class, "World not found: ", argStrVar)
                    .endControlFlow()
                    .addStatement("$L = tempWorld", varName);
        }
    }

    @Override
    protected void generatePlatformInlineResolveMulti(MethodSpec.Builder methodSpec, TypeName typeName, String varName, String argsVar, String argIdxVar, String senderVar, int i) {
        if (typeName.toString().equals("org.bukkit.Location")) {
            ClassName worldClass = ClassName.get("org.bukkit", "World");
            ClassName bukkitClass = ClassName.get("org.bukkit", "Bukkit");
            ClassName locationClass = ClassName.get("org.bukkit", "Location");

            methodSpec.addStatement("$T locWorld = $T.getWorld($L[$L])", worldClass, bukkitClass, argsVar, argIdxVar)
                    .beginControlFlow("if (locWorld == null)")
                    .addStatement("throw new $T($S + $L[$L])", IllegalArgumentException.class, "World not found: ", argsVar, argIdxVar)
                    .endControlFlow();

            methodSpec.beginControlFlow("try")
                    .addStatement("double x = $T.parseDouble($L[$L + 1])", Double.class, argsVar, argIdxVar)
                    .addStatement("double y = $T.parseDouble($L[$L + 2])", Double.class, argsVar, argIdxVar)
                    .addStatement("double z = $T.parseDouble($L[$L + 3])", Double.class, argsVar, argIdxVar)
                    .addStatement("$L = new $T(locWorld, x, y, z)", varName, locationClass)
                    .nextControlFlow("catch ($T e)", NumberFormatException.class)
                    .addStatement("throw new $T($S)", IllegalArgumentException.class, "Invalid coordinate format; must be numeric values.")
                    .endControlFlow();
        }
    }

    @Override
    protected void generatePlatformParamSuggestions(MethodSpec.Builder methodSpec, TypeName typeName, String senderCastVar, String argsVar, String currentVar, int tempIdx) {
        String name = typeName.toString();
        ClassName bukkitClass = ClassName.get("org.bukkit", "Bukkit");
        if (name.equals("org.bukkit.entity.Player") || name.equals("org.bukkit.OfflinePlayer")) {
            ClassName playerClass = ClassName.get("org.bukkit.entity", "Player");
            methodSpec.addStatement("$T list = new $T()", ParameterizedTypeName.get(List.class, String.class), ClassName.get("java.util", "ArrayList"))
                    .addStatement("String lower = current.toLowerCase()")
                    .beginControlFlow("for ($T onlinePlayer : $T.getOnlinePlayers())", playerClass, bukkitClass)
                    .beginControlFlow("if (onlinePlayer.getName().toLowerCase().startsWith(lower))")
                    .addStatement("list.add(onlinePlayer.getName())")
                    .endControlFlow()
                    .endControlFlow()
                    .addStatement("return list");
        } else if (name.equals("org.bukkit.World")) {
            ClassName worldClass = ClassName.get("org.bukkit", "World");
            methodSpec.addStatement("$T list = new $T()", ParameterizedTypeName.get(List.class, String.class), ClassName.get("java.util", "ArrayList"))
                    .addStatement("String lower = current.toLowerCase()")
                    .beginControlFlow("for ($T world : $T.getWorlds())", worldClass, bukkitClass)
                    .beginControlFlow("if (world.getName().toLowerCase().startsWith(lower))")
                    .addStatement("list.add(world.getName())")
                    .endControlFlow()
                    .endControlFlow()
                    .addStatement("return list");
        } else if (name.equals("org.bukkit.Location")) {
            ClassName worldClass = ClassName.get("org.bukkit", "World");
            methodSpec.beginControlFlow("if ($L.length - 1 == $L)", argsVar, tempIdx)
                    .addStatement("$T list = new $T()", ParameterizedTypeName.get(List.class, String.class), ClassName.get("java.util", "ArrayList"))
                    .addStatement("String lower = current.toLowerCase()")
                    .beginControlFlow("for ($T world : $T.getWorlds())", worldClass, bukkitClass)
                    .beginControlFlow("if (world.getName().toLowerCase().startsWith(lower))")
                    .addStatement("list.add(world.getName())")
                    .endControlFlow()
                    .endControlFlow()
                    .addStatement("return list")
                    .endControlFlow()
                    .addStatement("return $T.emptyList()", Collections.class);
        }
    }
}
