package io.github.projectunified.craftcommand.processor.standalone;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.MethodSpec;
import com.palantir.javapoet.ParameterizedTypeName;
import com.palantir.javapoet.TypeName;
import com.palantir.javapoet.TypeSpec;
import io.github.projectunified.craftcommand.processor.generator.BaseCommandGenerator;
import io.github.projectunified.craftcommand.processor.model.CommandModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Modifier;
import java.util.List;

/**
 * Generates {@code *$StandaloneCommand} wrappers.
 */
public final class StandaloneCommandGenerator extends BaseCommandGenerator {
    public StandaloneCommandGenerator(ProcessingEnvironment processingEnvironment) {
        super(processingEnvironment);
    }

    @Override
    protected String getWrapperClassSuffix() {
        return "$StandaloneCommand";
    }

    @Override
    protected void configureClass(TypeSpec.Builder typeSpec, CommandModel model) {
        typeSpec.addSuperinterface(ClassName.get("io.github.projectunified.craftcommand.standalone", "StandaloneCommand"));
    }

    @Override
    public ClassName getSenderTypeName() {
        return ClassName.get(Object.class);
    }

    @Override
    protected TypeName getManagerType() {
        return ParameterizedTypeName.get(ClassName.get("io.github.projectunified.craftcommand", "CommandManager"),
                ClassName.get(Object.class));
    }

    @Override
    protected void generateEntryMethods(TypeSpec.Builder typeSpec, CommandModel model) {
        typeSpec.addMethod(MethodSpec.methodBuilder("getName")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", model.getCommandName())
                .build());

        typeSpec.addMethod(MethodSpec.methodBuilder("getAliases")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addStatement("return $L", buildAliasesExpression(model))
                .build());

        typeSpec.addMethod(MethodSpec.methodBuilder("getDescription")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(String.class)
                .addStatement("return $S", model.getDescription())
                .build());

        MethodSpec.Builder executeSpec = MethodSpec.methodBuilder("execute")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(boolean.class)
                .addParameter(Object.class, "sender")
                .addParameter(String[].class, "args");
        generateExecuteMethodBody(executeSpec, model, "return true");
        typeSpec.addMethod(executeSpec.build());

        MethodSpec.Builder tabSpec = MethodSpec.methodBuilder("tabComplete")
                .addAnnotation(Override.class)
                .addModifiers(Modifier.PUBLIC)
                .returns(ParameterizedTypeName.get(List.class, String.class))
                .addParameter(Object.class, "sender")
                .addParameter(String[].class, "args");
        buildSuggestionRouting(tabSpec, model, "args", "instance", model);
        typeSpec.addMethod(tabSpec.build());
    }
}
