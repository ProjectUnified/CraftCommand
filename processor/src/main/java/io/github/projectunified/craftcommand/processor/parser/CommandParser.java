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
import java.util.*;

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

        String commandName;
        List<String> aliases;
        String description = "";

        if (commandAnn != null) {
            commandName = commandAnn.value();
            aliases = Arrays.asList(commandAnn.aliases());
            description = commandAnn.description();
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
                Command methodCommandAnn = method.getAnnotation(Command.class);

                // @Command on method = subcommand method
                boolean isSubcommandMethod = methodCommandAnn != null;

                if (defaultAnn != null && isSubcommandMethod) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "A method cannot be annotated with both @Default and @Command", method);
                    continue;
                }

                if (defaultAnn != null && !defaultAnn.value().isEmpty()) {
                    messager.printMessage(Diagnostic.Kind.WARNING,
                            "@Default(value) on a method is ignored. Use @Default (no value) on methods, or @Default(value) on parameters.", method);
                }

                if (defaultAnn == null && !isSubcommandMethod) {
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
                    Default paramDefaultAnn = param.getAnnotation(Default.class);
                    Greedy greedyAnn = param.getAnnotation(Greedy.class);
                    Name nameAnn = param.getAnnotation(Name.class);
                    Suggest suggestAnn = param.getAnnotation(Suggest.class);

                    String paramName = nameAnn != null ? nameAnn.value() : param.getSimpleName().toString();
                    TypeMirror paramType = param.asType();
                    boolean isOptional = paramDefaultAnn != null;
                    String defaultValue = (paramDefaultAnn != null && !paramDefaultAnn.value().isEmpty()) ? paramDefaultAnn.value() : null;
                    boolean isGreedy = greedyAnn != null;
                    String suggestProvider = suggestAnn != null ? suggestAnn.value() : null;

                    if (isGreedy && hasGreedy) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "A command method can only have at most one @Greedy parameter", method);
                    }
                    if (isGreedy && i != parameters.size() - 1) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "The @Greedy parameter must be the last parameter in the method", param);
                        continue;
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

                // Determine subcommand name, aliases, description from @Command
                String subName = null;
                List<String> subAliases = new ArrayList<>();
                String subDesc = "";

                if (methodCommandAnn != null) {
                    subName = methodCommandAnn.value();
                    subAliases = Arrays.asList(methodCommandAnn.aliases());
                    subDesc = methodCommandAnn.description();
                }

                MethodModel methodModel = new MethodModel(
                        method.getSimpleName().toString(),
                        subName,
                        subAliases,
                        subDesc,
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
                // @Command on nested class = subcommand class
                if (innerClass.getAnnotation(Command.class) != null) {
                    CommandModel nestedModel = parseClass(innerClass, env);
                    if (nestedModel != null) {
                        nestedSubcommands.add(nestedModel);
                    }
                }
            }
        }

        // Parse resolver methods from @Resolve annotations
        Map<String, MethodModel> resolverMethods = new HashMap<>();
        parseResolverMethods(typeElement, subcommands, defaultMethod, resolverMethods, env);

        return new CommandModel(className, packageName, commandName, aliases, description, defaultMethod, subcommands, nestedSubcommands, typeElement, resolverMethods);
    }

    /**
     * Parses resolver methods referenced by @Resolve annotations on method params.
     * Only parses methods that are actually referenced (simpler approach).
     */
    private static void parseResolverMethods(TypeElement typeElement, List<MethodModel> allMethods, MethodModel defaultMethod, Map<String, MethodModel> resolverMethods, ProcessingEnvironment env) {
        Messager messager = env.getMessager();

        // Collect all @Resolve names from all method params
        List<String> resolveNames = new ArrayList<>();
        if (defaultMethod != null) {
            for (ParameterModel p : defaultMethod.getParameters()) {
                Resolve resolveAnn = p.getElement().getAnnotation(Resolve.class);
                if (resolveAnn != null && !resolveAnn.value().isEmpty()) {
                    resolveNames.add(resolveAnn.value());
                }
            }
        }
        for (MethodModel m : allMethods) {
            for (ParameterModel p : m.getParameters()) {
                Resolve resolveAnn = p.getElement().getAnnotation(Resolve.class);
                if (resolveAnn != null && !resolveAnn.value().isEmpty()) {
                    resolveNames.add(resolveAnn.value());
                }
            }
        }

        // For each referenced resolver name, find and parse the method
        for (String resolveName : resolveNames) {
            if (resolverMethods.containsKey(resolveName)) continue;
            ExecutableElement resolverMethod = findMethodByName(typeElement, resolveName);
            if (resolverMethod == null) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Resolver method '" + resolveName + "' not found in " + typeElement.getSimpleName(), typeElement);
                continue;
            }
            MethodModel resolverModel = parseResolverMethod(resolverMethod, messager);
            resolverMethods.put(resolveName, resolverModel);
        }
    }

    private static ExecutableElement findMethodByName(TypeElement typeElement, String name) {
        // Search current class and parent classes
        TypeElement current = typeElement;
        while (current != null) {
            for (Element enclosed : current.getEnclosedElements()) {
                if (enclosed instanceof ExecutableElement && enclosed.getSimpleName().toString().equals(name)) {
                    return (ExecutableElement) enclosed;
                }
            }
            javax.lang.model.element.Element enclosing = current.getEnclosingElement();
            current = (enclosing instanceof TypeElement) ? (TypeElement) enclosing : null;
        }
        return null;
    }

    private static MethodModel parseResolverMethod(ExecutableElement method, Messager messager) {
        List<? extends VariableElement> parameters = method.getParameters();

        // All params are regular params (sender-type params are skipped during resolution via isSenderParam)
        ParameterModel senderParam = null;
        List<ParameterModel> paramModels = new ArrayList<>();

        for (VariableElement param : parameters) {
            Default paramDefaultAnn = param.getAnnotation(Default.class);
            Greedy greedyAnn = param.getAnnotation(Greedy.class);
            Name nameAnn = param.getAnnotation(Name.class);
            Suggest suggestAnn = param.getAnnotation(Suggest.class);

            String paramName = nameAnn != null ? nameAnn.value() : param.getSimpleName().toString();
            TypeMirror paramType = param.asType();
            boolean isOptional = paramDefaultAnn != null;
            String defaultValue = (paramDefaultAnn != null && !paramDefaultAnn.value().isEmpty()) ? paramDefaultAnn.value() : null;
            boolean isGreedy = greedyAnn != null;
            String suggestProvider = suggestAnn != null ? suggestAnn.value() : null;

            paramModels.add(new ParameterModel(paramName, paramType, isGreedy, isOptional, defaultValue, suggestProvider, param));
        }

        return new MethodModel(method.getSimpleName().toString(), null, null, null, senderParam, paramModels, false, method);
    }
}
