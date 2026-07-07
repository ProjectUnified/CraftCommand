package io.github.projectunified.craftcommand.processor.paper;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.github.projectunified.craftcommand.processor.ArrayExecutionSource;
import io.github.projectunified.craftcommand.processor.BaseCommandProcessor;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;

import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.Collection;
import java.util.List;

@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.projectunified.craftcommand.annotation.Command")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class PaperBasicCommandProcessor extends BaseCommandProcessor {

    final ClassName commandSourceStackClass = ClassName.get("io.papermc.paper.command.brigadier", "CommandSourceStack");

    @Override
    protected boolean isSenderType(TypeName typeName) {
        String name = typeName.toString();
        return name.equals("io.papermc.paper.command.brigadier.CommandSourceStack")
                || name.equals("org.bukkit.entity.Player")
                || name.equals("org.bukkit.command.ConsoleCommandSender")
                || name.equals("org.bukkit.command.BlockCommandSender")
                || name.equals("org.bukkit.command.CommandSender");
    }

    @Override
    protected boolean isSenderBaseType(TypeName typeName) {
        String name = typeName.toString();
        return name.equals("io.papermc.paper.command.brigadier.CommandSourceStack");
    }

    @Override
    protected boolean isPlatformBuiltInType(TypeName typeName) {
        String name = typeName.toString();
        return name.equals("org.bukkit.entity.Player")
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
    protected void generatePlatformParamSuggestions(MethodSpec.Builder methodSpec, TypeName typeName, String senderCastVar, String argsVar, String currentVar, int tempIdx) {
        methodSpec.addStatement("return $T.emptyList()", java.util.Collections.class);
    }

    @Override
    protected CodeBlock getSenderExpression(String senderVar) {
        return CodeBlock.of("$L.getSender()", senderVar);
    }

    @Override
    protected String getWrapperClassSuffix() {
        return "_PaperBasic";
    }

    @Override
    protected void configureSuperType(TypeSpec.Builder typeSpec) {
        typeSpec.addSuperinterface(ClassName.get("io.github.projectunified.craftcommand.paper", "PaperBasicCommand"));
    }

    @Override
    protected ClassName getSenderTypeName() {
        return ClassName.get("io.papermc.paper.command.brigadier", "CommandSourceStack");
    }

    @Override
    protected TypeName getManagerType() {
        ClassName commandManagerClass = ClassName.get("io.github.projectunified.craftcommand", "CommandManager");
        return ParameterizedTypeName.get(commandManagerClass, getSenderTypeName());
    }

    @Override
    protected void buildEntryMethods(TypeSpec.Builder typeSpec, CommandModel model, TypeElement typeElement) {
        // getName()
        typeSpec.addMethod(MethodSpec.methodBuilder("getName")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", model.getCommandName())
                .build());

        // getAliases()
        typeSpec.addMethod(MethodSpec.methodBuilder("getAliases")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(Collection.class, String.class))
                .addStatement("return $L", buildAliasesExpression(model))
                .build());

        // getDescription()
        typeSpec.addMethod(MethodSpec.methodBuilder("getDescription")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", model.getDescription())
                .build());

        // permission()
        Permission classPermission = model.getElement().getAnnotation(Permission.class);
        String permValue = null;
        if (classPermission != null) {
            permValue = classPermission.value();
        } else if (model.getDefaultMethod() != null) {
            Permission methodPermission = model.getDefaultMethod().getElement().getAnnotation(Permission.class);
            if (methodPermission != null) {
                permValue = methodPermission.value();
            }
        }
        typeSpec.addMethod(MethodSpec.methodBuilder("permission")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $L", permValue == null ? "null" : CodeBlock.of("$S", permValue))
                .build());

        // execute(CommandSourceStack sender, String[] args)
        MethodSpec.Builder executeSpec = MethodSpec.methodBuilder("execute")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .addParameter(getSenderTypeName(), "sender")
                .addParameter(String[].class, "args");

        generateExecuteMethodBody(executeSpec, model, "return");
        typeSpec.addMethod(executeSpec.build());

        // suggest(CommandSourceStack sender, String[] args)
        MethodSpec.Builder suggestSpec = MethodSpec.methodBuilder("suggest")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(Collection.class, String.class))
                .addParameter(getSenderTypeName(), "sender")
                .addParameter(String[].class, "args");

        buildSuggestionRouting(suggestSpec, model, "args", "instance", model);
        typeSpec.addMethod(suggestSpec.build());
    }

    @Override
    protected void resolvePlatformParameter(MethodSpec.Builder methodSpec, TypeName typeName, String varName, String argStrVar) {
        String name = typeName.toString();
        if (name.equals("org.bukkit.entity.Player")) {
            methodSpec.addStatement("$L = getPlayer($L)", varName, argStrVar);
        } else if (name.equals("org.bukkit.OfflinePlayer")) {
            methodSpec.addStatement("$L = getOfflinePlayer($L)", varName, argStrVar);
        } else if (name.equals("org.bukkit.World")) {
            methodSpec.addStatement("$L = getWorld($L)", varName, argStrVar);
        }
    }

    @Override
    protected void resolvePlatformMultiParameter(MethodSpec.Builder methodSpec, TypeName typeName, String varName, String argsVar, String argIdxVar, String senderVar, int i) {
        if (typeName.toString().equals("org.bukkit.Location")) {
            methodSpec.addStatement("$L = getLocation($L, $L)", varName, argsVar, argIdxVar);
        }
    }

    @Override
    protected void buildAdditionalHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        super.buildAdditionalHelpers(typeSpec, model);

        boolean hasPlayer = hasParameterType(model, "org.bukkit.entity.Player")
                || hasParameterType(model, "org.bukkit.OfflinePlayer");
        boolean hasWorld = hasParameterType(model, "org.bukkit.World")
                || hasParameterType(model, "org.bukkit.Location");

        ClassName bukkitClass = ClassName.get("org.bukkit", "Bukkit");

        if (hasPlayer) {
            ClassName playerClass = ClassName.get("org.bukkit.entity", "Player");
            typeSpec.addMethod(MethodSpec.methodBuilder("suggestPlayers")
                    .addJavadoc("Suggests online player names matching the current input (case-insensitive).\n\n"
                            + "@param current the current user input\n"
                            + "@return a list of matching player names\n")
                    .addModifiers(Modifier.PRIVATE)
                    .returns(ParameterizedTypeName.get(List.class, String.class))
                    .addParameter(String.class, "current")
                    .addStatement("$T list = new $T()", ParameterizedTypeName.get(List.class, String.class), ClassName.get("java.util", "ArrayList"))
                    .addStatement("String lower = current.toLowerCase()")
                    .beginControlFlow("for ($T onlinePlayer : $T.getOnlinePlayers())", playerClass, bukkitClass)
                    .beginControlFlow("if (onlinePlayer.getName().toLowerCase().startsWith(lower))")
                    .addStatement("list.add(onlinePlayer.getName())")
                    .endControlFlow()
                    .endControlFlow()
                    .addStatement("return list")
                    .build());
        }

        if (hasWorld) {
            ClassName worldClass = ClassName.get("org.bukkit", "World");
            typeSpec.addMethod(MethodSpec.methodBuilder("suggestWorlds")
                    .addJavadoc("Suggests world names matching the current input (case-insensitive).\n\n"
                            + "@param current the current user input\n"
                            + "@return a list of matching world names\n")
                    .addModifiers(Modifier.PRIVATE)
                    .returns(ParameterizedTypeName.get(List.class, String.class))
                    .addParameter(String.class, "current")
                    .addStatement("$T list = new $T()", ParameterizedTypeName.get(List.class, String.class), ClassName.get("java.util", "ArrayList"))
                    .addStatement("String lower = current.toLowerCase()")
                    .beginControlFlow("for ($T world : $T.getWorlds())", worldClass, bukkitClass)
                    .beginControlFlow("if (world.getName().toLowerCase().startsWith(lower))")
                    .addStatement("list.add(world.getName())")
                    .endControlFlow()
                    .endControlFlow()
                    .addStatement("return list")
                    .build());
        }

        if (hasParameterType(model, "org.bukkit.entity.Player")) {
            ClassName playerClass = ClassName.get("org.bukkit.entity", "Player");
            typeSpec.addMethod(MethodSpec.methodBuilder("getPlayer")
                    .addJavadoc("Retrieves an online player by name.\n\n"
                            + "@param name the player name\n"
                            + "@return the player, or null if name is null\n"
                            + "@throws IllegalArgumentException if the player is not found\n")
                    .addModifiers(Modifier.PRIVATE)
                    .returns(playerClass)
                    .addParameter(String.class, "name")
                    .beginControlFlow("if (name == null)")
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement("$T tempPlayer = $T.getPlayer(name)", playerClass, bukkitClass)
                    .beginControlFlow("if (tempPlayer == null)")
                    .addStatement("throw new $T($S + name)", IllegalArgumentException.class, "Player not found: ")
                    .endControlFlow()
                    .addStatement("return tempPlayer")
                    .build());
        }

        if (hasParameterType(model, "org.bukkit.OfflinePlayer")) {
            ClassName offlinePlayerClass = ClassName.get("org.bukkit", "OfflinePlayer");
            typeSpec.addMethod(MethodSpec.methodBuilder("getOfflinePlayer")
                    .addJavadoc("Retrieves an offline player by name.\n\n"
                            + "@param name the player name\n"
                            + "@return the offline player, or null if name is null\n"
                            + "@throws IllegalArgumentException if the player has not played before\n")
                    .addModifiers(Modifier.PRIVATE)
                    .returns(offlinePlayerClass)
                    .addParameter(String.class, "name")
                    .beginControlFlow("if (name == null)")
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement("$T tempOffline = $T.getOfflinePlayer(name)", offlinePlayerClass, bukkitClass)
                    .beginControlFlow("if (!tempOffline.hasPlayedBefore())")
                    .addStatement("throw new $T($S + name)", IllegalArgumentException.class, "Player not found: ")
                    .endControlFlow()
                    .addStatement("return tempOffline")
                    .build());
        }

        if (hasParameterType(model, "org.bukkit.World") || hasParameterType(model, "org.bukkit.Location")) {
            ClassName worldClass = ClassName.get("org.bukkit", "World");
            typeSpec.addMethod(MethodSpec.methodBuilder("getWorld")
                    .addJavadoc("Retrieves a world by name.\n\n"
                            + "@param name the world name\n"
                            + "@return the world, or null if name is null\n"
                            + "@throws IllegalArgumentException if the world is not found\n")
                    .addModifiers(Modifier.PRIVATE)
                    .returns(worldClass)
                    .addParameter(String.class, "name")
                    .beginControlFlow("if (name == null)")
                    .addStatement("return null")
                    .endControlFlow()
                    .addStatement("$T tempWorld = $T.getWorld(name)", worldClass, bukkitClass)
                    .beginControlFlow("if (tempWorld == null)")
                    .addStatement("throw new $T($S + name)", IllegalArgumentException.class, "World not found: ")
                    .endControlFlow()
                    .addStatement("return tempWorld")
                    .build());
        }

        if (hasParameterType(model, "org.bukkit.Location")) {
            ClassName locationClass = ClassName.get("org.bukkit", "Location");
            ClassName worldClass = ClassName.get("org.bukkit", "World");
            typeSpec.addMethod(MethodSpec.methodBuilder("getLocation")
                    .addJavadoc("Retrieves a Location from arguments.\n\n"
                            + "@param args the command arguments\n"
                            + "@param startIdx the index of the world argument\n"
                            + "@return the resolved location\n"
                            + "@throws IllegalArgumentException if the coordinates or world name are invalid\n")
                    .addModifiers(Modifier.PRIVATE)
                    .returns(locationClass)
                    .addParameter(String[].class, "args")
                    .addParameter(int.class, "startIdx")
                    .addStatement("$T locWorld = getWorld(args[startIdx])", worldClass)
                    .beginControlFlow("try")
                    .addStatement("double x = $T.parseDouble(args[startIdx + 1])", Double.class)
                    .addStatement("double y = $T.parseDouble(args[startIdx + 2])", Double.class)
                    .addStatement("double z = $T.parseDouble(args[startIdx + 3])", Double.class)
                    .addStatement("return new $T(locWorld, x, y, z)", locationClass)
                    .nextControlFlow("catch ($T e)", NumberFormatException.class)
                    .addStatement("throw new $T($S)", IllegalArgumentException.class, "Invalid coordinate format; must be numeric values.")
                    .endControlFlow()
                    .build());
        }
    }

    private boolean hasParameterType(CommandModel model, String typeName) {
        if (model.getDefaultMethod() != null && hasParameterType(model.getDefaultMethod(), typeName)) {
            return true;
        }
        for (MethodModel sub : model.getSubcommands()) {
            if (hasParameterType(sub, typeName)) {
                return true;
            }
        }
        for (CommandModel child : model.getNestedSubcommands()) {
            if (hasParameterType(child, typeName)) {
                return true;
            }
        }
        return false;
    }

    private boolean hasParameterType(MethodModel method, String typeName) {
        for (io.github.projectunified.craftcommand.processor.model.ParameterModel p : method.getParameters()) {
            if (TypeName.get(p.getType()).toString().equals(typeName)) {
                return true;
            }
        }
        return false;
    }
}
