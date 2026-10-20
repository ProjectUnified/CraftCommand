package io.github.projectunified.craftcommand.processor.parser;

import com.palantir.javapoet.ClassName;
import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.annotation.Default;
import io.github.projectunified.craftcommand.annotation.Greedy;
import io.github.projectunified.craftcommand.annotation.Name;
import io.github.projectunified.craftcommand.annotation.Resolve;
import io.github.projectunified.craftcommand.annotation.Suggest;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.MethodModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.annotation.processing.Messager;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Reads the annotations of one command class into a {@link CommandModel} tree.
 *
 * <p>This is the only place that reads the core command annotations: generators consume the models and do not
 * re-read them. Resolver methods are linked recursively at parse time, so
 * {@link ParameterModel#getResolverMethod()} is already populated for nested resolver parameters.
 */
final class CommandParser {
    private final Messager messager;

    CommandParser(Messager messager) {
        this.messager = messager;
    }

    /**
     * Parses a class annotated with {@code @Command}.
     *
     * @param typeElement the command class
     * @return the command model, or {@code null} when the class cannot be parsed
     */
    CommandModel parse(TypeElement typeElement) {
        Command commandAnn = typeElement.getAnnotation(Command.class);
        if (commandAnn == null) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Class must be annotated with @Command", typeElement);
            return null;
        }
        return parseClass(typeElement, commandAnn);
    }

    private CommandModel parseClass(TypeElement typeElement, Command commandAnn) {
        ClassName className = ClassName.get(typeElement);
        String packageName = className.packageName();

        MethodModel defaultMethod = null;
        List<MethodModel> subcommands = new ArrayList<>();
        List<CommandModel> nestedSubcommands = new ArrayList<>();

        Resolvers resolvers = new Resolvers(typeElement, messager);

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement) {
                ExecutableElement method = (ExecutableElement) enclosed;
                Default defaultAnn = method.getAnnotation(Default.class);
                Command methodCommandAnn = method.getAnnotation(Command.class);
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

                VariableElement senderParam = parameters.get(0);
                Name senderNameAnn = senderParam.getAnnotation(Name.class);
                Resolve senderResolveAnn = senderParam.getAnnotation(Resolve.class);
                String senderResolveName = senderResolveAnn != null ? senderResolveAnn.value() : null;
                ParameterModel senderParamModel = new ParameterModel(
                        senderNameAnn != null ? senderNameAnn.value() : senderParam.getSimpleName().toString(),
                        senderParam.asType(),
                        false,
                        false,
                        null,
                        null,
                        false,
                        senderResolveName,
                        resolvers.resolve(senderResolveName),
                        null,
                        senderParam
                );

                List<ParameterModel> paramModels = new ArrayList<>();
                boolean hasOptional = false;
                boolean hasGreedy = false;

                for (int i = 1; i < parameters.size(); i++) {
                    VariableElement param = parameters.get(i);
                    ParameterModel paramModel = parseParameter(param, resolvers);

                    if (paramModel.isGreedy() && hasGreedy) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "A command method can only have at most one @Greedy parameter", method);
                    }
                    if (paramModel.isGreedy() && i != parameters.size() - 1) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "The @Greedy parameter must be the last parameter in the method", param);
                        continue;
                    }

                    if (paramModel.isOptional()) {
                        hasOptional = true;
                    } else if (hasOptional && !paramModel.isGreedy()) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "Required parameters cannot follow optional parameters", param);
                    }

                    if (paramModel.isGreedy()) {
                        hasGreedy = true;
                    }

                    paramModels.add(paramModel);
                }

                String subName = methodCommandAnn != null ? methodCommandAnn.value() : null;
                List<String> subAliases = methodCommandAnn != null ? new ArrayList<>(List.of(methodCommandAnn.aliases())) : new ArrayList<>();
                String subDesc = methodCommandAnn != null ? methodCommandAnn.description() : "";

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
                Command innerCommand = innerClass.getAnnotation(Command.class);
                if (innerCommand != null) {
                    CommandModel nestedModel = parseClass(innerClass, innerCommand);
                    if (nestedModel != null) {
                        nestedSubcommands.add(nestedModel);
                    }
                }
            }
        }

        resolvers.reportMissing();

        return new CommandModel(className, packageName, commandAnn.value(), new ArrayList<>(List.of(commandAnn.aliases())),
                commandAnn.description(), defaultMethod, subcommands, nestedSubcommands, typeElement);
    }

    private ParameterModel parseParameter(VariableElement param, Resolvers resolvers) {
        Default defaultAnn = param.getAnnotation(Default.class);
        Name nameAnn = param.getAnnotation(Name.class);
        Suggest suggestAnn = param.getAnnotation(Suggest.class);
        Resolve resolveAnn = param.getAnnotation(Resolve.class);

        String suggestProvider = suggestAnn != null ? suggestAnn.value() : null;
        MethodModel suggestMethod = parseSuggestMethod(suggestProvider, resolvers);
        boolean suggestField = suggestProvider != null && suggestMethod == null
                && ResolverLookup.isField(resolvers.owner, suggestProvider);
        if (suggestProvider != null && suggestMethod == null && !suggestField) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Could not find field or method '" + suggestProvider + "' for suggestions in " + resolvers.owner.getSimpleName(), param);
        }

        String resolveName = resolveAnn != null ? resolveAnn.value() : null;
        return new ParameterModel(
                nameAnn != null ? nameAnn.value() : param.getSimpleName().toString(),
                param.asType(),
                param.getAnnotation(Greedy.class) != null,
                defaultAnn != null,
                (defaultAnn != null && !defaultAnn.value().isEmpty()) ? defaultAnn.value() : null,
                suggestProvider,
                suggestField,
                resolveName,
                resolvers.resolve(resolveName),
                suggestMethod,
                param
        );
    }

    private MethodModel parseSuggestMethod(String suggestProvider, Resolvers resolvers) {
        TypeElement owner = resolvers.owner;
        if (suggestProvider == null || suggestProvider.isEmpty() || owner == null) return null;
        ExecutableElement suggestMethod = ResolverLookup.findSuggestMethod(owner, suggestProvider);
        return suggestMethod != null ? parseResolverMethod(suggestMethod, resolvers) : null;
    }

    /**
     * Parses a resolver or suggestion provider method. Nested {@code @Resolve} parameters are linked through the
     * owning class resolver set.
     */
    private MethodModel parseResolverMethod(ExecutableElement method, Resolvers resolvers) {
        List<ParameterModel> paramModels = new ArrayList<>();
        for (VariableElement param : method.getParameters()) {
            paramModels.add(parseParameter(param, resolvers));
        }
        return new MethodModel(method.getSimpleName().toString(), null, null, null, null, paramModels, false, method);
    }

    /**
     * Resolver methods referenced by {@code @Resolve} names within one command class.
     */
    private final class Resolvers {
        private final TypeElement owner;
        private final Messager messager;
        private final Map<String, MethodModel> methods = new HashMap<>();
        private final Set<String> missing = new LinkedHashSet<>();
        private final Set<String> resolving = new LinkedHashSet<>();

        private Resolvers(TypeElement owner, Messager messager) {
            this.owner = owner;
            this.messager = messager;
        }

        /**
         * Links the resolver named by an {@code @Resolve} value, parsing it on first use.
         *
         * @param name the raw {@code @Resolve} value, {@code null} when absent
         * @return the resolver method model, or {@code null} when absent or unresolvable
         */
        private MethodModel resolve(String name) {
            if (name == null || name.isEmpty() || owner == null) return null;
            MethodModel cached = methods.get(name);
            if (cached != null) return cached;
            if (!resolving.add(name)) return null;

            ExecutableElement method = ResolverLookup.findMethod(owner, name);
            if (method == null) {
                missing.add(name);
                resolving.remove(name);
                return null;
            }
            MethodModel model = parseResolverMethod(method, this);
            methods.put(name, model);
            resolving.remove(name);
            return model;
        }

        private void reportMissing() {
            for (String name : missing) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Resolver method '" + name + "' not found in " + owner.getSimpleName(), owner);
            }
        }
    }
}
