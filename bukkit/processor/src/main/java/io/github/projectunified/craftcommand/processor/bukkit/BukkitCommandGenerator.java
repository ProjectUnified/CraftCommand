package io.github.projectunified.craftcommand.processor.bukkit;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.CodeBlock;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.github.projectunified.craftcommand.processor.generator.BaseCommandGenerator;
import io.github.projectunified.craftcommand.processor.generator.TypeSupport;
import io.github.projectunified.craftcommand.processor.model.CommandModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.Modifier;
import java.util.List;

/**
 * Generates {@code *$BukkitCommand} wrappers.
 */
public final class BukkitCommandGenerator extends BaseCommandGenerator {
    private final ClassName commandSenderClass = ClassName.get("org.bukkit.command", "CommandSender");

    public BukkitCommandGenerator(ProcessingEnvironment processingEnvironment) {
        super(processingEnvironment);

        senderTypeRegistry().registerSenderBaseType("java.lang.Object");
        senderTypeRegistry().registerSenderBaseType("org.bukkit.command.CommandSender");
        senderTypeRegistry().registerSenderType("org.bukkit.entity.Player");
        senderTypeRegistry().registerSenderType("org.bukkit.command.ConsoleCommandSender");
        senderTypeRegistry().registerSenderType("org.bukkit.command.BlockCommandSender");

        ClassName playerClass = ClassName.get("org.bukkit.entity", "Player");
        ClassName offlinePlayerClass = ClassName.get("org.bukkit", "OfflinePlayer");
        ClassName worldClass = ClassName.get("org.bukkit", "World");
        ClassName locationClass = ClassName.get("org.bukkit", "Location");

        typeSupport().register(TypeSupport.Entry.builder(playerClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null"))
                .parseExpr(arg -> CodeBlock.of("getPlayer($L)", arg))
                .platformSuggestions((spec, p) -> spec.addStatement("return suggestPlayers($L)", p.currentVar()))
                .build());
        typeSupport().register(TypeSupport.Entry.builder(offlinePlayerClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null"))
                .parseExpr(arg -> CodeBlock.of("getOfflinePlayer($L)", arg))
                .platformSuggestions((spec, p) -> spec.addStatement("return suggestPlayers($L)", p.currentVar()))
                .build());
        typeSupport().register(TypeSupport.Entry.builder(worldClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null"))
                .parseExpr(arg -> CodeBlock.of("getWorld($L)", arg))
                .platformSuggestions((spec, p) -> spec.addStatement("return suggestWorlds($L)", p.currentVar()))
                .build());
        typeSupport().register(TypeSupport.Entry.builder(locationClass, 3)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null"))
                .platformMultiResolution((spec, p) -> spec.addStatement("$L = getLocation($L, $L, $L)", p.varName(), p.argsVar(), p.index(), p.senderVar()))
                .build());
    }

    private static Permission findPermissionUp(Element element) {
        Element current = element;
        while (current != null) {
            Permission permission = current.getAnnotation(Permission.class);
            if (permission != null) return permission;
            current = current.getEnclosingElement();
        }
        return null;
    }

    @Override
    protected String getWrapperClassSuffix() {
        return "$BukkitCommand";
    }

    @Override
    protected void configureClass(TypeSpec.Builder typeSpec, CommandModel model) {
        typeSpec.superclass(ClassName.get("org.bukkit.command", "Command"));
        typeSpec.addSuperinterface(ClassName.get("io.github.projectunified.craftcommand", "BaseCommand"));
    }

    @Override
    public ClassName getSenderTypeName() {
        return commandSenderClass;
    }

    @Override
    protected TypeName getManagerType() {
        return ParameterizedTypeName.get(ClassName.get("io.github.projectunified.craftcommand", "CommandManager"),
                commandSenderClass);
    }

    @Override
    protected void addConstructorStatements(MethodSpec.Builder constructorBuilder, CommandModel model) {
        constructorBuilder.addStatement("super($S)", model.getCommandName());
        constructorBuilder.addStatement("this.setDescription($S)", model.getDescription());
        constructorBuilder.addStatement("this.setAliases($L)", buildAliasesExpression(model));
    }

    @Override
    protected void generateEntryMethods(TypeSpec.Builder typeSpec, CommandModel model) {
        MethodSpec.Builder executeSpec = MethodSpec.methodBuilder("execute")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(commandSenderClass, "sender")
                .addParameter(String.class, "label")
                .addParameter(String[].class, "args");
        generateExecuteMethodBody(executeSpec, model, "return true");
        typeSpec.addMethod(executeSpec.build());

        MethodSpec.Builder tabSpec = MethodSpec.methodBuilder("tabComplete")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addParameter(commandSenderClass, "sender")
                .addParameter(String.class, "alias")
                .addParameter(String[].class, "args");
        buildSuggestionRouting(tabSpec, model, "args", model);
        typeSpec.addMethod(tabSpec.build());
    }

    @Override
    protected void generatePlatformHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        if (hasDefaultPermission(model)) typeSpec.addMethod(permissionCheck());
        BukkitHelperMethods.generate(typeSpec, model);
    }

    private static boolean hasDefaultPermission(CommandModel model) {
        if (anyMethod(model, BukkitCommandGenerator::usesDefaultPermission)) return true;
        for (CommandModel child : model.getNestedSubcommands()) {
            if (usesDefaultPermission(child.getElement()) || hasDefaultPermission(child)) return true;
        }
        return false;
    }

    private static boolean usesDefaultPermission(Element element) {
        Permission permission = findPermissionUp(element);
        return permission != null && permission.message().isEmpty();
    }

    private MethodSpec permissionCheck() {
        return MethodSpec.methodBuilder("checkPermission")
                .addModifiers(Modifier.PRIVATE)
                .returns(boolean.class)
                .addParameter(commandSenderClass, "sender")
                .addParameter(String.class, "permission")
                .addStatement("if (sender.hasPermission(permission)) return true")
                .addStatement("manager.sendMessage(sender, $S, $S, permission)",
                        PERMISSION_KEY, DEFAULT_PERMISSION_MESSAGE)
                .addStatement("return false")
                .build();
    }

    @Override
    protected void onBeforeExecute(MethodSpec.Builder methodSpec, Element element, String returnStatement) {
        Permission permission = findPermissionUp(element);
        if (permission != null) {
            generatePermissionCheck(methodSpec, permission, returnStatement);
        }
    }

    private void generatePermissionCheck(MethodSpec.Builder methodSpec, Permission permission, String returnStatement) {
        String message = permission.message();
        if (message.isEmpty()) {
            methodSpec.addStatement("if (!checkPermission(sender, $S)) $L", permission.value(), returnStatement);
            return;
        }
        methodSpec.beginControlFlow("if (!sender.hasPermission($S))", permission.value());
        if (isI18nKey(message)) {
            methodSpec.addStatement("manager.sendMessage(sender, $S, $S, $S)",
                    i18nKey(message), message, permission.value());
        } else {
            methodSpec.addStatement("manager.sendMessage(sender, $S, $S)", message, message);
        }
        methodSpec.addStatement(returnStatement).endControlFlow();
    }
}
