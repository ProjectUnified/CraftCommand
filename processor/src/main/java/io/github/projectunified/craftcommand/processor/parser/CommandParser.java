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
import java.util.List;
import java.util.Map;

/**
 * Reads the annotations of one command class into a {@link CommandModel} tree.
 *
 * <p>This is the only place that reads the core command annotations: generators consume the models and do not
 * re-read them. Resolver methods are linked recursively at parse time, so
 * {@link ParameterModel#getResolverMethod()} is already populated for nested resolver parameters.
 *
 * @param messager the annotation processor messager
 */
record CommandParser(Messager messager) {

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
        var resolvers = new Resolvers(typeElement);

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement executable) {
                var method = parseMethod(executable, resolvers);
                if (method == null) continue;

                if (!method.isDefault()) {
                    subcommands.add(method);
                } else if (defaultMethod != null) {
                    messager.printMessage(Diagnostic.Kind.ERROR, "Multiple @Default methods are not allowed", method.element());
                } else {
                    defaultMethod = method;
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
        var resolveName = resolveNameOf(sender);
        return ParameterModel.sender(nameOf(sender), sender.asType(), resolveName, resolvers.resolve(resolveName), sender);
    }

    private static String nameOf(VariableElement parameter) {
        var nameAnn = parameter.getAnnotation(Name.class);
        return nameAnn != null ? nameAnn.value() : parameter.getSimpleName().toString();
    }

    private static String resolveNameOf(VariableElement parameter) {
        var resolveAnn = parameter.getAnnotation(Resolve.class);
        return resolveAnn != null ? resolveAnn.value() : null;
    }

    private List<ParameterModel> parseParameters(ExecutableElement method, Resolvers resolvers) {
        var parameters = method.getParameters();
        var models = new ArrayList<ParameterModel>();
        boolean hasOptional = false;

        for (int i = 1; i < parameters.size(); i++) {
            var parameter = parseParameter(parameters.get(i), resolvers);
            boolean greedy = parameter.greedy();
            if (greedy && i != parameters.size() - 1) {
                messager.printMessage(Diagnostic.Kind.ERROR, "The @Greedy parameter must be the last parameter in the method", parameters.get(i));
                continue;
            }
            if (parameter.optional()) {
                hasOptional = true;
            } else if (hasOptional && !greedy) {
                messager.printMessage(Diagnostic.Kind.ERROR, "Required parameters cannot follow optional parameters", parameters.get(i));
            }
            models.add(parameter);
        }
        return models;
    }

    private static List<String> enumConstants(TypeMirror type) {
        if (!(type instanceof DeclaredType declaredType)) return null;
        var element = declaredType.asElement();
        if (element.getKind() != ElementKind.ENUM) return null;

        return element.getEnclosedElements().stream()
                .filter(enclosed -> enclosed.getKind() == ElementKind.ENUM_CONSTANT)
                .map(enclosed -> enclosed.getSimpleName().toString())
                .toList();
    }

    private ParameterModel parseParameter(VariableElement param, Resolvers resolvers) {
        var defaultAnn = param.getAnnotation(Default.class);
        var suggestAnn = param.getAnnotation(Suggest.class);

        var suggestProvider = suggestAnn != null ? suggestAnn.value() : null;
        MethodModel suggestMethod = null;
        if (suggestProvider != null && !suggestProvider.isEmpty()) {
            var method = ResolverLookup.findSuggestMethod(resolvers.owner, suggestProvider);
            if (method != null) suggestMethod = parseResolverMethod(method, resolvers);
        }
        boolean suggestField = suggestProvider != null && suggestMethod == null
                && ResolverLookup.isField(resolvers.owner, suggestProvider);
        if (suggestProvider != null && suggestMethod == null && !suggestField) {
            messager.printMessage(Diagnostic.Kind.ERROR,
                    "Could not find field or method '" + suggestProvider + "' for suggestions in " + resolvers.owner.getSimpleName(), param);
        }

        var resolveName = resolveNameOf(param);
        return new ParameterModel(
                nameOf(param),
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

    /**
     * Parses a resolver or suggestion provider method. Nested {@code @Resolve} parameters are linked through the
     * owning class resolver set.
     */
    private MethodModel parseResolverMethod(ExecutableElement method, Resolvers resolvers) {
        var parameters = method.getParameters().stream()
                .map(parameter -> parseParameter(parameter, resolvers))
                .toList();
        return new MethodModel(method.getSimpleName().toString(), null, null, null, null, parameters, false, method);
    }

    /**
     * Resolver methods referenced by {@code @Resolve} names within one command class.
     */
    private final class Resolvers {
        private final TypeElement owner;
        private final Map<String, MethodModel> methods = new HashMap<>();

        private Resolvers(TypeElement owner) {
            this.owner = owner;
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

            methods.put(name, null); // also breaks resolver cycles
            var method = ResolverLookup.findMethod(owner, name);
            if (method == null) {
                messager.printMessage(Diagnostic.Kind.ERROR,
                        "Resolver method '" + name + "' not found in " + owner.getSimpleName(), owner);
                return null;
            }
            var model = parseResolverMethod(method, this);
            methods.put(name, model);
            return model;
        }
    }
}
