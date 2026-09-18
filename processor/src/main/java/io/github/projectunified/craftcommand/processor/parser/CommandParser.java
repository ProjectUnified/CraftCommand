package io.github.projectunified.craftcommand.processor.parser;

import com.palantir.javapoet.ClassName;
import io.github.projectunified.craftcommand.processor.*;
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
public final class CommandParser {

    private CommandParser() {
    }

    /**
     * Parses the given TypeElement if annotated with {@code @Command}.
     *
     * @param typeElement the class element
     * @param env         the processing environment
     * @return the parsed CommandModel, or {@code null} if parsing failed
     */
    public static CommandModel parse(TypeElement typeElement, ProcessingEnvironment env) {
        CommandPrism commandAnn = CommandPrism.getInstanceOn(typeElement);
        if (commandAnn == null) {
            env.getMessager().printMessage(Diagnostic.Kind.ERROR, "Class must be annotated with @Command", typeElement);
            return null;
        }
        return parseClass(typeElement, env);
    }

    private static CommandModel parseClass(TypeElement typeElement, ProcessingEnvironment env) {
        Messager messager = env.getMessager();

        CommandPrism commandAnn = CommandPrism.getInstanceOn(typeElement);

        String commandName;
        List<String> aliases;
        String description = "";

        if (commandAnn != null) {
            commandName = commandAnn.value();
            aliases = new ArrayList<>(commandAnn.aliases());
            description = commandAnn.description();
        } else {
            return null;
        }

        ClassName className = ClassName.get(typeElement);
        String packageName = className.packageName();

        MethodModel defaultMethod = null;
        List<MethodModel> subcommands = new ArrayList<>();
        List<CommandModel> nestedSubcommands = new ArrayList<>();

        // Resolve the methods referenced by @Resolve before building parameters, so every parameter model
        // links its resolver directly.
        Resolvers resolvers = resolveReferencedResolvers(typeElement);

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement) {
                ExecutableElement method = (ExecutableElement) enclosed;
                DefaultPrism defaultAnn = DefaultPrism.getInstanceOn(method);
                CommandPrism methodCommandAnn = CommandPrism.getInstanceOn(method);

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
                NamePrism senderNameAnn = NamePrism.getInstanceOn(senderParam);
                String senderName = senderNameAnn != null ? senderNameAnn.value() : senderParam.getSimpleName().toString();
                ResolvePrism senderResolveAnn = ResolvePrism.getInstanceOn(senderParam);
                String senderResolveName = senderResolveAnn != null ? senderResolveAnn.value() : null;
                ParameterModel senderParamModel = new ParameterModel(
                        senderName,
                        senderParam.asType(),
                        false,
                        false,
                        null,
                        null,
                        senderResolveName,
                        resolveSenderMethod(typeElement, senderResolveName),
                        null,
                        senderParam
                );

                List<ParameterModel> paramModels = new ArrayList<>();

                boolean hasOptional = false;
                boolean hasGreedy = false;

                for (int i = 1; i < parameters.size(); i++) {
                    VariableElement param = parameters.get(i);
                    ParameterModel paramModel = parseParameter(param, typeElement, resolvers);

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

                // Determine subcommand name, aliases, description from @Command
                String subName = null;
                List<String> subAliases = new ArrayList<>();
                String subDesc = "";

                if (methodCommandAnn != null) {
                    subName = methodCommandAnn.value();
                    subAliases = new ArrayList<>(methodCommandAnn.aliases());
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
                if (CommandPrism.isPresent(innerClass)) {
                    CommandModel nestedModel = parseClass(innerClass, env);
                    if (nestedModel != null) {
                        nestedSubcommands.add(nestedModel);
                    }
                }
            }
        }

        // Report resolvers referenced by this class that could not be found.
        for (String missing : resolvers.missing) {
            messager.printMessage(Diagnostic.Kind.ERROR, "Resolver method '" + missing + "' not found in " + typeElement.getSimpleName(), typeElement);
        }

        return new CommandModel(className, packageName, commandName, aliases, description, defaultMethod, subcommands, nestedSubcommands, typeElement);
    }

    /**
     * Resolves every resolver method referenced by a {@code @Resolve} name on the command methods of this class.
     *
     * <p>Names that cannot be resolved are collected separately so the caller can report them once, after the
     * per-method diagnostics.
     */
    private static Resolvers resolveReferencedResolvers(TypeElement typeElement) {
        Map<String, MethodModel> methods = new LinkedHashMap<>();
        List<String> missing = new ArrayList<>();

        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (!(enclosed instanceof ExecutableElement)) continue;
            ExecutableElement method = (ExecutableElement) enclosed;
            if (DefaultPrism.getInstanceOn(method) == null && CommandPrism.getInstanceOn(method) == null) continue;

            List<? extends VariableElement> parameters = method.getParameters();
            for (int i = 1; i < parameters.size(); i++) {
                ResolvePrism resolveAnn = ResolvePrism.getInstanceOn(parameters.get(i));
                if (resolveAnn == null || resolveAnn.value().isEmpty()) continue;

                String resolveName = resolveAnn.value();
                if (methods.containsKey(resolveName) || missing.contains(resolveName)) continue;

                ExecutableElement resolverMethod = ResolverLookup.findMethod(typeElement, resolveName);
                if (resolverMethod == null) {
                    missing.add(resolveName);
                } else {
                    methods.put(resolveName, parseResolverMethod(resolverMethod));
                }
            }
        }
        return new Resolvers(methods, missing);
    }

    /**
     * Resolves the resolver declared by a command method's sender parameter.
     *
     * <p>Senders are resolved independently of {@link Resolvers}, which covers the remaining parameters.
     */
    private static MethodModel resolveSenderMethod(TypeElement typeElement, String resolveName) {
        if (resolveName == null || resolveName.isEmpty()) return null;
        ExecutableElement resolverMethod = ResolverLookup.findMethod(typeElement, resolveName);
        return resolverMethod != null ? parseResolverMethod(resolverMethod) : null;
    }

    private static ParameterModel parseParameter(VariableElement param, TypeElement owner, Resolvers resolvers) {
        DefaultPrism paramDefaultAnn = DefaultPrism.getInstanceOn(param);
        NamePrism nameAnn = NamePrism.getInstanceOn(param);
        SuggestPrism suggestAnn = SuggestPrism.getInstanceOn(param);
        ResolvePrism resolveAnn = ResolvePrism.getInstanceOn(param);

        String paramName = nameAnn != null ? nameAnn.value() : param.getSimpleName().toString();
        TypeMirror paramType = param.asType();
        boolean isOptional = paramDefaultAnn != null;
        String defaultValue = (paramDefaultAnn != null && !paramDefaultAnn.value().isEmpty()) ? paramDefaultAnn.value() : null;
        boolean isGreedy = GreedyPrism.isPresent(param);
        String suggestProvider = suggestAnn != null ? suggestAnn.value() : null;
        String resolveName = resolveAnn != null ? resolveAnn.value() : null;

        return new ParameterModel(paramName, paramType, isGreedy, isOptional, defaultValue, suggestProvider,
                resolveName, resolvers.methodFor(resolveName), parseSuggestMethod(owner, suggestProvider), param);
    }

    /**
     * Parses the suggestion provider method named by {@code @Suggest}, when that name is a valid method.
     */
    private static MethodModel parseSuggestMethod(TypeElement owner, String suggestProvider) {
        if (suggestProvider == null || suggestProvider.isEmpty() || owner == null) return null;
        ExecutableElement suggestMethod = ResolverLookup.findSuggestMethod(owner, suggestProvider);
        return suggestMethod != null ? parseResolverMethod(suggestMethod) : null;
    }

    /**
     * Parses a resolver or suggestion provider method into a model.
     *
     * <p>Annotation reads stay inside the parser: generators call this instead of inspecting annotations
     * themselves.
     *
     * @param method the method element
     * @return the method model
     */
    public static MethodModel parseResolverMethod(ExecutableElement method) {
        List<? extends VariableElement> parameters = method.getParameters();
        TypeElement owner = method.getEnclosingElement() instanceof TypeElement ? (TypeElement) method.getEnclosingElement() : null;
        List<ParameterModel> paramModels = new ArrayList<>();
        for (VariableElement param : parameters) {
            paramModels.add(parseParameter(param, owner, Resolvers.NONE));
        }
        return new MethodModel(method.getSimpleName().toString(), null, null, null, null, paramModels, false, method);
    }

    /**
     * Resolver methods referenced by {@code @Resolve} names within one command class.
     */
    private static final class Resolvers {
        private static final Resolvers NONE = new Resolvers(Collections.emptyMap(), Collections.emptyList());

        private final Map<String, MethodModel> methods;
        private final List<String> missing;

        private Resolvers(Map<String, MethodModel> methods, List<String> missing) {
            this.methods = methods;
            this.missing = missing;
        }

        /**
         * Gets the resolver model for a raw {@code @Resolve} value.
         *
         * @param resolveName the raw {@code @Resolve} value, {@code null} when absent
         * @return the resolver method model, or {@code null} when there is no named or resolvable resolver
         */
        private MethodModel methodFor(String resolveName) {
            if (resolveName == null || resolveName.isEmpty()) return null;
            return methods.get(resolveName);
        }
    }
}
