package io.github.projectunified.craftcommand.processor.bukkit;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.github.projectunified.craftcommand.processor.BaseCommandProcessor;
import io.github.projectunified.craftcommand.processor.TypeSupport;
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
public class BukkitCommandProcessor extends BaseCommandProcessor {

    private final ClassName commandSenderClass = ClassName.get("org.bukkit.command", "CommandSender");
    private final ClassName chatColorClass = ClassName.get("org.bukkit", "ChatColor");

    {
        ClassName playerClass = ClassName.get("org.bukkit.entity", "Player");
        ClassName offlinePlayerClass = ClassName.get("org.bukkit", "OfflinePlayer");
        ClassName worldClass = ClassName.get("org.bukkit", "World");
        ClassName locationClass = ClassName.get("org.bukkit", "Location");

        typeSupport().register(TypeSupport.Entry.builder(playerClass, 1)
                .primitiveDefault("null")
                .literal(d -> CodeBlock.of("null"))
                .platformResolution((spec, p) -> spec.addStatement("$L = getPlayer($L)", p[0], p[1]))
                .platformSuggestions((spec, p) -> spec.addStatement("return suggestPlayers($L)", p[2]))
                .build());
        typeSupport().register(TypeSupport.Entry.builder(offlinePlayerClass, 1)
                .primitiveDefault("null")
                .literal(d -> CodeBlock.of("null"))
                .platformResolution((spec, p) -> spec.addStatement("$L = getOfflinePlayer($L)", p[0], p[1]))
                .platformSuggestions((spec, p) -> spec.addStatement("return suggestPlayers($L)", p[2]))
                .build());
        typeSupport().register(TypeSupport.Entry.builder(worldClass, 1)
                .primitiveDefault("null")
                .literal(d -> CodeBlock.of("null"))
                .platformResolution((spec, p) -> spec.addStatement("$L = getWorld($L)", p[0], p[1]))
                .platformSuggestions((spec, p) -> spec.addStatement("return suggestWorlds($L)", p[2]))
                .build());
        typeSupport().register(TypeSupport.Entry.builder(locationClass, 4)
                .primitiveDefault("null")
                .literal(d -> CodeBlock.of("null"))
                .platformMultiResolution((spec, p) -> spec.addStatement("$L = getLocation($L, $L)", p[0], p[1], p[2]))
                .platformSuggestions((spec, p) -> {
                    String argsVar = p[1];
                    String currentVar = p[2];
                    String tempIdx = p[3];
                    spec.beginControlFlow("if ($L.length - 1 == $L)", argsVar, tempIdx)
                            .addStatement("return suggestWorlds($L)", currentVar)
                            .endControlFlow()
                            .addStatement("return $T.emptyList()", Collections.class);
                })
                .build());
    }

    @Override
    protected String getWrapperClassSuffix() {
        return "_Executor";
    }

    @Override
    protected void configureSuperType(TypeSpec.Builder typeSpec) {
        typeSpec.superclass(ClassName.get("org.bukkit.command", "Command"));
    }

    @Override
    protected TypeName getCommandInterfaceType() {
        return ClassName.get("org.bukkit.command", "Command");
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
    protected boolean isSenderType(TypeName typeName) {
        String name = typeName.toString();
        return isSenderBaseType(typeName)
                || name.equals("org.bukkit.entity.Player")
                || name.equals("org.bukkit.command.ConsoleCommandSender")
                || name.equals("org.bukkit.command.BlockCommandSender");
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
    protected void buildEntryMethods(TypeSpec.Builder typeSpec, CommandModel model, TypeElement typeElement) {
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

        buildSuggestionRouting(tabSpec, model, "args", "instance", model);
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
    protected void buildAdditionalHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        super.buildAdditionalHelpers(typeSpec, model);
        BukkitHelperMethods.generate(typeSpec, model);
    }
}
