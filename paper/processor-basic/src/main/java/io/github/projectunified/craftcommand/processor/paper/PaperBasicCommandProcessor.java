package io.github.projectunified.craftcommand.processor.paper;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.bukkit.annotation.Permission;
import io.github.projectunified.craftcommand.processor.BaseCommandProcessor;
import io.github.projectunified.craftcommand.processor.TypeSupport;
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

    {
        senderTypeRegistry().registerSenderBaseType("io.papermc.paper.command.brigadier.CommandSourceStack");
        senderTypeRegistry().registerSenderType("org.bukkit.entity.Player");
        senderTypeRegistry().registerSenderType("org.bukkit.command.ConsoleCommandSender");
        senderTypeRegistry().registerSenderType("org.bukkit.command.BlockCommandSender");
        senderTypeRegistry().registerSenderType("org.bukkit.command.CommandSender");
    }

    @Override
    protected void registerTypes(TypeSupport types) {
        ClassName playerClass = ClassName.get("org.bukkit.entity", "Player");
        ClassName worldClass = ClassName.get("org.bukkit", "World");
        ClassName locationClass = ClassName.get("org.bukkit", "Location");
        types.register(TypeSupport.Entry.builder(playerClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null"))
                .platformResolution((spec, p) -> spec.addStatement("$L = getPlayer($L)", p[0], p[1])).build());
        types.register(TypeSupport.Entry.builder(worldClass, 1)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null"))
                .platformResolution((spec, p) -> spec.addStatement("$L = getWorld($L)", p[0], p[1])).build());
        types.register(TypeSupport.Entry.builder(locationClass, 4)
                .primitiveDefault("null").literal(d -> CodeBlock.of("null"))
                .platformMultiResolution((spec, p) -> spec.addStatement("$L = getLocation($L, $L)", p[0], p[1], p[2])).build());
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
    protected void anchorConfigureType(TypeSpec.Builder typeSpec) {
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
    protected void anchorBuildEntryMethods(TypeSpec.Builder typeSpec, CommandModel model, TypeElement typeElement) {
        typeSpec.addMethod(MethodSpec.methodBuilder("getName")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(String.class)
                .addStatement("return $S", model.getCommandName()).build());
        typeSpec.addMethod(MethodSpec.methodBuilder("getAliases")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(Collection.class, String.class))
                .addStatement("return $L", buildAliasesExpression(model)).build());
        typeSpec.addMethod(MethodSpec.methodBuilder("getDescription")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(String.class)
                .addStatement("return $S", model.getDescription()).build());

        Permission classPermission = model.getElement().getAnnotation(Permission.class);
        String permValue = null;
        if (classPermission != null) {
            permValue = classPermission.value();
        } else if (model.getDefaultMethod() != null) {
            Permission methodPermission = model.getDefaultMethod().getElement().getAnnotation(Permission.class);
            if (methodPermission != null) permValue = methodPermission.value();
        }
        typeSpec.addMethod(MethodSpec.methodBuilder("permission")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(String.class)
                .addStatement("return $L", permValue == null ? "null" : CodeBlock.of("$S", permValue)).build());

        MethodSpec.Builder executeSpec = MethodSpec.methodBuilder("execute")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .addParameter(getSenderTypeName(), "sender")
                .addParameter(String[].class, "args");
        generateExecuteMethodBody(executeSpec, model, "return");
        typeSpec.addMethod(executeSpec.build());

        MethodSpec.Builder suggestSpec = MethodSpec.methodBuilder("suggest")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(Collection.class, String.class))
                .addParameter(getSenderTypeName(), "sender")
                .addParameter(String[].class, "args");
        buildSuggestionRouting(suggestSpec, model, "args", "instance", model);
        typeSpec.addMethod(suggestSpec.build());
    }

    @Override
    protected void anchorAdditionalHelpers(TypeSpec.Builder typeSpec, CommandModel model) {
        super.anchorAdditionalHelpers(typeSpec, model);
        BukkitHelperMethods.generate(typeSpec, model);
    }
}
