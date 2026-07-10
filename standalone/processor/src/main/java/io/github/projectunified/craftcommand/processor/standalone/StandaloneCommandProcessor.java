package io.github.projectunified.craftcommand.processor.standalone;

import com.google.auto.service.AutoService;
import com.palantir.javapoet.*;
import io.github.projectunified.craftcommand.processor.BaseCommandProcessor;
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
public class StandaloneCommandProcessor extends BaseCommandProcessor {

    @Override
    protected String getWrapperClassSuffix() {
        return "_Standalone";
    }

    @Override
    protected void anchorConfigureType(TypeSpec.Builder typeSpec) {
        typeSpec.addSuperinterface(ClassName.get("io.github.projectunified.craftcommand.standalone", "StandaloneCommand"));
    }

    @Override
    protected ClassName getSenderTypeName() {
        return ClassName.get(Object.class);
    }

    @Override
    protected TypeName getManagerType() {
        ClassName commandManagerClass = ClassName.get("io.github.projectunified.craftcommand", "CommandManager");
        return ParameterizedTypeName.get(commandManagerClass, ClassName.get(Object.class));
    }

    @Override
    protected void anchorBuildEntryMethods(TypeSpec.Builder typeSpec, CommandModel model, TypeElement typeElement) {
        typeSpec.addMethod(MethodSpec.methodBuilder("getName")
                .addJavadoc("Gets the main name of the command.\n\n@return the command name\n")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(String.class)
                .addStatement("return $S", model.getCommandName()).build());
        typeSpec.addMethod(MethodSpec.methodBuilder("getAliases")
                .addJavadoc("Gets all registered aliases for the command.\n\n@return a list of command aliases\n")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addStatement("return $L", buildAliasesExpression(model)).build());
        typeSpec.addMethod(MethodSpec.methodBuilder("getDescription")
                .addJavadoc("Gets the description of the command.\n\n@return the command description\n")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(String.class)
                .addStatement("return $S", model.getDescription()).build());

        MethodSpec.Builder executeSpec = MethodSpec.methodBuilder("execute")
                .addJavadoc("Executes the command.\n\n"
                        + "@param sender the execution initiator\n"
                        + "@param args command arguments\n"
                        + "@return true if executed successfully\n")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC).returns(boolean.class)
                .addParameter(Object.class, "sender")
                .addParameter(String[].class, "args");
        generateExecuteMethodBody(executeSpec, model, "return true");
        typeSpec.addMethod(executeSpec.build());

        MethodSpec.Builder tabSpec = MethodSpec.methodBuilder("tabComplete")
                .addJavadoc("Provides suggestions for tab completion.\n\n"
                        + "@param sender the execution initiator\n"
                        + "@param args command arguments\n"
                        + "@return suggestions list\n")
                .addAnnotation(Override.class).addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addParameter(Object.class, "sender")
                .addParameter(String[].class, "args");
        buildSuggestionRouting(tabSpec, model, "args", "instance", model);
        typeSpec.addMethod(tabSpec.build());
    }
}
