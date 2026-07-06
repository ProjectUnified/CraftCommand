package io.github.projectunified.craftcommand.processor.parser;

import com.palantir.javapoet.ClassName;
import io.github.projectunified.craftcommand.annotation.*;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.annotation.processing.Messager;
import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Parser utility to convert compile-time annotated class elements into structured command models.
 */
public class CommandParser {

    /**
     * Parses the given TypeElement if annotated with {@code @Command}.
     *
     * @param typeElement the class element
     * @param env         the processing environment
     * @return the parsed CommandModel, or {@code null} if parsing failed
     */
    public static CommandModel parse(TypeElement typeElement, ProcessingEnvironment env) {
        Command commandAnn = typeElement.getAnnotation(Command.class);
        if (commandAnn == null) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Class must be annotated with @Command", typeElement);
            return null;
        }
        return parseClass(typeElement, env);
    }

    private static CommandModel parseClass(TypeElement typeElement, ProcessingEnvironment env) {
        Messager messager = env.getMessager();

        Command commandAnn = typeElement.getAnnotation(Command.class);
        Subcommand classSubcommandAnn = typeElement.getAnnotation(Subcommand.class);

        String commandName;
        List<String> aliases;
        String description = "";

        if (commandAnn != null) {
            commandName = commandAnn.value();
            aliases = Arrays.asList(commandAnn.aliases());
            description = commandAnn.description();
        } else if (classSubcommandAnn != null) {
            commandName = classSubcommandAnn.value();
            aliases = Arrays.asList(classSubcommandAnn.aliases());
        } else {
            return null;
        }

        ClassName className = ClassName.get(typeElement);
        String packageName = className.packageName();

        MethodModel defaultMethod = null;
        List<MethodModel> subcommands = new ArrayList<>();
        List<CommandModel> nestedSubcommands = new ArrayList<>();

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement) {
                ExecutableElement method = (ExecutableElement) enclosed;
                Default defaultAnn = method.getAnnotation(Default.class);
                Subcommand subcommandAnn = method.getAnnotation(Subcommand.class);

                if (defaultAnn != null && subcommandAnn != null) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "A method cannot be annotated with both @Default and @Subcommand", method);
                    continue;
                }

                if (defaultAnn == null && subcommandAnn == null) {
                    continue;
                }

                List<? extends VariableElement> parameters = method.getParameters();
                if (parameters.isEmpty()) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Command method must have at least one parameter (the sender)", method);
                    continue;
                }

                // First parameter is the sender
                VariableElement senderParam = parameters.get(0);
                Name senderNameAnn = senderParam.getAnnotation(Name.class);
                String senderName = senderNameAnn != null ? senderNameAnn.value() : senderParam.getSimpleName().toString();
                ParameterModel senderParamModel = new ParameterModel(
                        senderName,
                        senderParam.asType(),
                        false,
                        false,
                        null,
                        null,
                        senderParam
                );

                List<ParameterModel> paramModels = new ArrayList<>();

                boolean hasOptional = false;
                boolean hasGreedy = false;

                for (int i = 1; i < parameters.size(); i++) {
                    VariableElement param = parameters.get(i);
                    Optional optionalAnn = param.getAnnotation(Optional.class);
                    Greedy greedyAnn = param.getAnnotation(Greedy.class);
                    Name nameAnn = param.getAnnotation(Name.class);
                    Suggest suggestAnn = param.getAnnotation(Suggest.class);

                    String paramName = nameAnn != null ? nameAnn.value() : param.getSimpleName().toString();
                    TypeMirror paramType = param.asType();
                    boolean isOptional = optionalAnn != null;
                    String defaultValue = isOptional ? optionalAnn.value() : null;
                    boolean isGreedy = greedyAnn != null;
                    String suggestProvider = suggestAnn != null ? suggestAnn.value() : null;

                    if (isGreedy && hasGreedy) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "A command method can only have at most one @Greedy parameter", method);
                    }
                    if (isGreedy && i != parameters.size() - 1) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "The @Greedy parameter must be the last parameter in the method", param);
                    }

                    if (isOptional) {
                        hasOptional = true;
                    } else if (hasOptional && !isGreedy) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "Required parameters cannot follow optional parameters", param);
                    }

                    if (isGreedy) {
                        hasGreedy = true;
                    }

                    paramModels.add(new ParameterModel(paramName, paramType, isGreedy, isOptional, defaultValue, suggestProvider, param));
                }

                MethodModel methodModel = new MethodModel(
                        method.getSimpleName().toString(),
                        subcommandAnn != null ? subcommandAnn.value() : null,
                        subcommandAnn != null ? Arrays.asList(subcommandAnn.aliases()) : new ArrayList<>(),
                        senderParamModel,
                        paramModels,
                        defaultAnn != null,
                        method
                );

                if (defaultAnn != null) {
                    if (defaultMethod != null) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "Multiple @Default methods are not allowed", method);
                    } else {
                        defaultMethod = methodModel;
                    }
                } else {
                    subcommands.add(methodModel);
                }
            } else if (enclosed instanceof TypeElement) {
                TypeElement innerClass = (TypeElement) enclosed;
                if (innerClass.getAnnotation(Subcommand.class) != null) {
                    CommandModel nestedModel = parseClass(innerClass, env);
                    if (nestedModel != null) {
                        nestedSubcommands.add(nestedModel);
                    }
                }
            }
        }

        return new CommandModel(className, packageName, commandName, aliases, description, defaultMethod, subcommands, nestedSubcommands, typeElement);
    }
}
