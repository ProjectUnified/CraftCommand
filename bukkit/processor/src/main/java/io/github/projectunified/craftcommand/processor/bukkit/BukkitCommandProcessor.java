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
import java.util.List;

@AutoService(Processor.class)
@SupportedAnnotationTypes("io.github.projectunified.craftcommand.annotation.Command")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
public class BukkitCommandProcessor extends BaseCommandProcessor {

    private final ClassName commandSenderClass = ClassName.get("org.bukkit.command", "CommandSender");
    private final ClassName chatColorClass = ClassName.get("org.bukkit", "ChatColor");

    {
        senderTypeRegistry().registerSenderBaseType("java.lang.Object");
        senderTypeRegistry().registerSenderBaseType("org.bukkit.command.CommandSender");
        senderTypeRegistry().registerSenderType("org.bukkit.entity.Player");
        senderTypeRegistry().registerSenderType("org.bukkit.command.ConsoleCommandSender");
        senderTypeRegistry().registerSenderType("org.bukkit.command.BlockCommandSender");
    }

    @Override
    protected void registerTypes(TypeSupport types) {
        ClassName playerClass = ClassName.get("org.bukkit.entity", "Player");
        ClassName offlinePlayerClass = ClassName.get("org.bukkit", "OfflinePlayer");
        ClassName worldClass = ClassName.get("org.bukkit", "World");
        ClassName locationClass = ClassName.get("org.bukkit", "Location");

        types.register(TypeSupport.Entry.builder(playerClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null"))
                .platformResolution((spec, p) -> spec.addStatement("$L = getPlayer($L)", p[0], p[1]))
                .platformSuggestions((spec, p) -> spec.addStatement("return suggestPlayers($L)", p[2]))
                .build());
        types.register(TypeSupport.Entry.builder(offlinePlayerClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null"))
                .platformResolution((spec, p) -> spec.addStatement("$L = getOfflinePlayer($L)", p[0], p[1]))
                .platformSuggestions((spec, p) -> spec.addStatement("return suggestPlayers($L)", p[2]))
                .build());
        types.register(TypeSupport.Entry.builder(worldClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null"))
                .platformResolution((spec, p) -> spec.addStatement("$L = getWorld($L)", p[0], p[1]))
                .platformSuggestions((spec, p) -> spec.addStatement("return suggestWorlds($L)", p[2]))
                .build());
        types.register(TypeSupport.Entry.builder(locationClass, 4)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null"))
                .platformMultiResolution((spec, p) -> spec.addStatement("$L = getLocation($L, $L)", p[0], p[1], p[2]))
                .platformSuggestions((spec, p) -> {
                    String argsVar = p[1];
                    String currentVar = p[2];
                    String tempIdx = p[3];
                    spec.beginControlFlow("if ($L.length - 1 == $L)", argsVar, tempIdx)
                            .addStatement("return suggestWorlds($L)", currentVar)
                            .endControlFlow()
                            .addStatement("return $T.emptyList()", java.util.Collections.class);
                })
                .build());
    }

    @Override
    protected String getWrapperClassSuffix() {
        return "_Executor";
    }

    @Override
    protected void anchorConfigureType(TypeSpec.Builder typeSpec) {
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
    protected void anchorConstructorTop(MethodSpec.Builder constructorBuilder, CommandModel model) {
        constructorBuilder.addStatement("super($S)", model.getCommandName());
        constructorBuilder.addStatement("this.setDescription($S)", model.getDescription());
        constructorBuilder.addStatement("this.setAliases($L)", buildAliasesExpression(model));
    }

    @Override
    protected void anchorBuildEntryMethods(TypeSpec.Builder typeSpec, CommandModel model, TypeElement typeElement) {
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
    protected void anchorAdditionalHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        super.anchorAdditionalHelpers(typeSpec, model);
        BukkitHelperMethods.generate(typeSpec, model);
    }

    @Override
    protected void generateUnknownSubcommandMessage(MethodSpec.Builder methodSpec, CommandModel model) {
        methodSpec.addStatement("sender.sendMessage($T.RED + $S)", chatColorClass, "Unknown subcommand. Available: " + getSubcommandNames(model));
    }

    @Override
    protected void onBeforeExecute(MethodSpec.Builder methodSpec, javax.lang.model.element.Element element, String returnStatement) {
        Permission permission = findPermission(element);
        if (permission != null) {
            generatePermissionCheck(methodSpec, permission, returnStatement);
        }
    }

    private Permission findPermission(javax.lang.model.element.Element element) {
        Permission perm = element.getAnnotation(Permission.class);
        if (perm != null) return perm;
        javax.lang.model.element.Element enclosing = element.getEnclosingElement();
        while (enclosing != null) {
            perm = enclosing.getAnnotation(Permission.class);
            if (perm != null) return perm;
            enclosing = enclosing.getEnclosingElement();
        }
        return null;
    }

    private void generatePermissionCheck(MethodSpec.Builder methodSpec, Permission permission, String returnStatement) {
        methodSpec.beginControlFlow("if (!sender.hasPermission($S))", permission.value());
        String msg = permission.message();
        if (!msg.isEmpty() && msg.startsWith("i18n:")) {
            String key = msg.substring(5);
            methodSpec.addStatement("sender.sendMessage($T.RED + manager.formatMessage($S, $S, $S))",
                    chatColorClass, key, msg, permission.value());
        } else if (!msg.isEmpty()) {
            methodSpec.addStatement("sender.sendMessage($S)", msg);
        } else {
            methodSpec.addStatement("sender.sendMessage($T.RED + manager.formatMessage($S, $S, $S))",
                    chatColorClass, "permission", "You do not have permission to execute this command.", permission.value());
        }
        methodSpec.addStatement(returnStatement).endControlFlow();
    }
}
