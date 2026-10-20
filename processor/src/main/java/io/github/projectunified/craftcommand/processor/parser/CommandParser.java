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
import javax.lang.model.element.ElementKind;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;
import javax.tools.Diagnostic;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
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
        var commandAnn = typeElement.getAnnotation(Command.class);
        if (commandAnn == null) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Class must be annotated with @Command", typeElement);
            return null;
        }
        return parseClass(typeElement, commandAnn);
    }

    private CommandModel parseClass(TypeElement typeElement, Command commandAnn) {
        MethodModel defaultMethod = null;
        var subcommands = new ArrayList<MethodModel>();
        var nestedSubcommands = new ArrayList<CommandModel>();
        var resolvers = new Resolvers(typeElement, messager);

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement executable) {
                var method = parseMethod(executable, resolvers);
                if (method == null) continue;

                if (method.isDefault()) {
                    if (defaultMethod != null) {
                        messager.printMessage(Diagnostic.Kind.ERROR, "Multiple @Default methods are not allowed", method.getElement());
                    } else {
                        defaultMethod = method;
                    }
                } else {
                    subcommands.add(method);
                }
            } else if (enclosed instanceof TypeElement innerClass) {
                var innerCommand = innerClass.getAnnotation(Command.class);
                if (innerCommand != null) {
                    nestedSubcommands.add(parseClass(innerClass, innerCommand));
                }
            }
        }

        var className = ClassName.get(typeElement);
        return new CommandModel(className, className.packageName(),
                commandAnn.value(), List.of(commandAnn.aliases()), commandAnn.description(),
                defaultMethod, subcommands, nestedSubcommands, typeElement);
    }

    private MethodModel parseMethod(ExecutableElement method, Resolvers resolvers) {
        var defaultAnn = method.getAnnotation(Default.class);
        var commandAnn = method.getAnnotation(Command.class);
        if (defaultAnn == null && commandAnn == null) return null;

        if (defaultAnn != null && commandAnn != null) {
            messager.printMessage(Diagnostic.Kind.ERROR, "A method cannot be annotated with both @Default and @Command", method);
            return null;
        }
        if (defaultAnn != null && !defaultAnn.value().isEmpty()) {
            messager.printMessage(Diagnostic.Kind.WARNING,
                    "@Default(value) on a method is ignored. Use @Default (no value) on methods, or @Default(value) on parameters.", method);
        }
        var parameters = method.getParameters();
        if (parameters.isEmpty()) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Command method must have at least one parameter (the sender)", method);
            return null;
        }

        var aliases = commandAnn != null ? List.of(commandAnn.aliases()) : List.<String>of();
        return new MethodModel(
                method.getSimpleName().toString(),
                commandAnn != null ? commandAnn.value() : null,
                aliases,
                commandAnn != null ? commandAnn.description() : "",
                parseSender(parameters.get(0), resolvers),
                parseParameters(method, resolvers),
                defaultAnn != null,
                method
        );
    }

    private ParameterModel parseSender(VariableElement sender, Resolvers resolvers) {
        var nameAnn = sender.getAnnotation(Name.class);
        var resolveAnn = sender.getAnnotation(Resolve.class);
        var resolveName = resolveAnn != null ? resolveAnn.value() : null;
        return new ParameterModel(
                nameAnn != null ? nameAnn.value() : sender.getSimpleName().toString(),
                sender.asType(),
                null,
                false,
                false,
                null,
                null,
                false,
                resolveName,
                resolvers.resolve(resolveName),
                null,
                sender
        );
    }

    private List<ParameterModel> parseParameters(ExecutableElement method, Resolvers resolvers) {
        var parameters = method.getParameters();
        var models = new ArrayList<ParameterModel>();
        boolean hasOptional = false;
        boolean hasGreedy = false;

        for (int i = 1; i < parameters.size(); i++) {
            var parameter = parseParameter(parameters.get(i), resolvers);

            if (parameter.isGreedy() && hasGreedy) {
                messager.printMessage(Diagnostic.Kind.ERROR, "A command method can only have at most one @Greedy parameter", method);
            }
            if (parameter.isGreedy() && i != parameters.size() - 1) {
                messager.printMessage(Diagnostic.Kind.ERROR, "The @Greedy parameter must be the last parameter in the method", parameters.get(i));
                continue;
            }
            if (parameter.isOptional()) {
                hasOptional = true;
            } else if (hasOptional && !parameter.isGreedy()) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Required parameters cannot follow optional parameters", parameters.get(i));
            }
            if (parameter.isGreedy()) {
                hasGreedy = true;
            }

            models.add(parameter);
        }
        return models;
    }

    private static List<String> enumConstants(TypeMirror type) {
        if (!(type instanceof DeclaredType declaredType)) return null;
        Element element = declaredType.asElement();
        if (element.getKind() != ElementKind.ENUM) return null;

        return element.getEnclosedElements().stream()
                .filter(enclosed -> enclosed.getKind() == ElementKind.ENUM_CONSTANT)
                .map(enclosed -> enclosed.getSimpleName().toString())
                .toList();
    }

    private ParameterModel parseParameter(VariableElement param, Resolvers resolvers) {
        var defaultAnn = param.getAnnotation(Default.class);
        var nameAnn = param.getAnnotation(Name.class);
        var suggestAnn = param.getAnnotation(Suggest.class);
        var resolveAnn = param.getAnnotation(Resolve.class);

        var suggestProvider = suggestAnn != null ? suggestAnn.value() : null;
        var suggestMethod = parseSuggestMethod(suggestProvider, resolvers);
        boolean suggestField = suggestProvider != null && suggestMethod == null
                && ResolverLookup.isField(resolvers.owner, suggestProvider);
        if (suggestProvider != null && suggestMethod == null && !suggestField) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Could not find field or method '" + suggestProvider + "' for suggestions in " + resolvers.owner.getSimpleName(), param);
        }

        var resolveName = resolveAnn != null ? resolveAnn.value() : null;
        return new ParameterModel(
                nameAnn != null ? nameAnn.value() : param.getSimpleName().toString(),
                param.asType(),
                enumConstants(param.asType()),
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
        if (suggestProvider == null || suggestProvider.isEmpty()) return null;
        var suggestMethod = ResolverLookup.findSuggestMethod(resolvers.owner, suggestProvider);
        return suggestMethod != null ? parseResolverMethod(suggestMethod, resolvers) : null;
    }

    /**
     * Parses a resolver or suggestion provider method. Nested {@code @Resolve} parameters are linked through the
     * owning class resolver set.
     */
    private MethodModel parseResolverMethod(ExecutableElement method, Resolvers resolvers) {
        var parameters = new ArrayList<ParameterModel>();
        for (var parameter : method.getParameters()) {
            parameters.add(parseParameter(parameter, resolvers));
        }
        return new MethodModel(method.getSimpleName().toString(), null, null, null, null, parameters, false, method);
    }

    /**
     * Resolver methods referenced by {@code @Resolve} names within one command class.
     */
    private final class Resolvers {
        private final TypeElement owner;
        private final Messager messager;
        private final Map<String, MethodModel> methods = new HashMap<>();
        private final Set<String> resolving = new HashSet<>();

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
            if (name == null || name.isEmpty()) return null;
            var cached = methods.get(name);
            if (cached != null || methods.containsKey(name)) return cached;
            if (!resolving.add(name)) return null;

            try {
                var method = ResolverLookup.findMethod(owner, name);
                if (method == null) {
                    methods.put(name, null);
                    messager.printMessage(Diagnostic.Kind.ERROR,
                            "Resolver method '" + name + "' not found in " + owner.getSimpleName(), owner);
                    return null;
                }
                var model = parseResolverMethod(method, this);
                methods.put(name, model);
                return model;
            } finally {
                resolving.remove(name);
            }
        }
    }
}
