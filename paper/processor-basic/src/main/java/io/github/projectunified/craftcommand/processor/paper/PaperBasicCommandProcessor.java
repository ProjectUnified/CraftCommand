package io.github.projectunified.craftcommand.processor.paper;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.github.projectunified.craftcommand.processor.BaseCommandProcessor;
import io.github.projectunified.craftcommand.processor.bukkit.BukkitHelperMethods;
import io.github.projectunified.craftcommand.processor.model.CommandModel;

import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;
import javax.lang.model.element.Modifier;
import javax.lang.model.element.TypeElement;
import java.util.Collection;

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
    protected TypeName getCommandInterfaceType() {
        return ClassName.get("io.github.projectunified.craftcommand.paper", "PaperBasicCommand");
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
        BukkitHelperMethods.generate(typeSpec, model);
    }
}
