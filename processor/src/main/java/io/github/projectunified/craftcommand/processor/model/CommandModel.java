package io.github.projectunified.craftcommand.processor.model;

import com.palantir.javapoet.ClassName;

import javax.lang.model.element.TypeElement;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * Model representing a parsed command or subcommand class.
 */
public class CommandModel {
    private final ClassName className;
    private final String packageName;
    private final String commandName;
    private final List<String> aliases;
    private final String description;
    private final MethodModel defaultMethod;
    private final List<MethodModel> subcommands;
    private final List<CommandModel> nestedSubcommands;
    private final TypeElement element;
    private final Map<String, MethodModel> resolverMethods;

    public CommandModel(ClassName className, String packageName, String commandName, List<String> aliases, String description, MethodModel defaultMethod, List<MethodModel> subcommands, List<CommandModel> nestedSubcommands, TypeElement element, Map<String, MethodModel> resolverMethods) {
        this.className = className;
        this.packageName = packageName;
        this.commandName = commandName;
        this.aliases = aliases;
        this.description = description != null ? description : "";
        this.defaultMethod = defaultMethod;
        this.subcommands = subcommands;
        this.nestedSubcommands = nestedSubcommands;
        this.element = element;
        this.resolverMethods = resolverMethods != null ? resolverMethods : Collections.emptyMap();
    }

    /**
     * Gets the ClassName of the command class.
     *
     * @return the class name
     */
    public ClassName getClassName() {
        return className;
    }

    /**
     * Gets the package name of the command class.
     *
     * @return the package name
     */
    public String getPackageName() {
        return packageName;
    }

    /**
     * Gets the primary name of the command.
     *
     * @return the command name
     */
    public String getCommandName() {
        return commandName;
    }

    /**
     * Gets the aliases of the command.
     *
     * @return the list of aliases
     */
    public List<String> getAliases() {
        return aliases;
    }

    /**
     * Gets the command description.
     *
     * @return the description
     */
    public String getDescription() {
        return description;
    }

    /**
     * Gets the default execution method.
     *
     * @return the default method model, or {@code null} if none
     */
    public MethodModel getDefaultMethod() {
        return defaultMethod;
    }

    /**
     * Gets the method-level subcommands.
     *
     * @return the list of subcommands
     */
    public List<MethodModel> getSubcommands() {
        return subcommands;
    }

    /**
     * Gets the nested class-level subcommands.
     *
     * @return the list of nested subcommands
     */
    public List<CommandModel> getNestedSubcommands() {
        return nestedSubcommands;
    }

    /**
     * Gets the underlying Java TypeElement of this class.
     *
     * @return the type element
     */
    public TypeElement getElement() {
        return element;
    }

    /**
     * Gets the resolver method models (from @Resolve annotations).
     *
     * @return map of resolver method name to MethodModel
     */
    public Map<String, MethodModel> getResolverMethods() {
        return resolverMethods;
    }

    /**
     * Gets a resolver method by name.
     *
     * @param name the resolver method name
     * @return the MethodModel, or null if not found
     */
    public MethodModel getResolverMethod(String name) {
        return resolverMethods.get(name);
    }
}
