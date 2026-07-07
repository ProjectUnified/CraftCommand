package io.github.projectunified.craftcommand;

import io.github.projectunified.craftcommand.exception.CommandException;

import java.util.*;

/**
 * Base command manager responsible for registering argument resolvers and handling errors.
 *
 * @param <S> the type of command sender
 */
public class CommandManager<S> {
    private final Map<Class<?>, ArgumentResolver<S, ?>> resolvers = new HashMap<>();
    private final List<ArgumentResolverProvider<S>> providers = new ArrayList<>();
    private final Map<Object, CommandInfoExposer> exposers = new java.util.concurrent.ConcurrentHashMap<>();
    private ErrorHandler<S> errorHandler;

    /**
     * Constructs a CommandManager with the specified error handler.
     *
     * @param errorHandler the error handler
     */
    public CommandManager(ErrorHandler<S> errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     * Filters the list of suggestions by checking if they start with the current input (case-insensitive).
     *
     * @param suggestions the raw list of suggestions
     * @param current     the current user input to filter by
     * @return the filtered list of suggestions
     */
    public static List<String> filterSuggestions(List<String> suggestions, String current) {
        if (suggestions == null) {
            return Collections.emptyList();
        }
        List<String> result = new ArrayList<>();
        String lower = current.toLowerCase();
        for (String s : suggestions) {
            if (s.toLowerCase().startsWith(lower)) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Formats a message template for a key with the given arguments.
     * Subclasses can override this method to translate/customize messages based on the key.
     *
     * @param key          the message key (e.g. "missing-argument", "validation.min")
     * @param defaultValue the default template to use
     * @param args         the arguments to format the template with
     * @return the formatted message
     */
    public String formatMessage(String key, String defaultValue, Object... args) {
        try {
            return String.format(defaultValue, args);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Registers a custom argument resolver for a specific type.
     *
     * @param type     the target class type
     * @param resolver the resolver instance
     * @param <T>      the type of parameter
     */
    public <T> void registerResolver(Class<T> type, ArgumentResolver<S, T> resolver) {
        resolvers.put(type, resolver);
    }

    /**
     * Registers an argument resolver provider for dynamic type resolution.
     *
     * @param provider the resolver provider
     */
    public void registerProvider(ArgumentResolverProvider<S> provider) {
        providers.add(provider);
    }

    /**
     * Resolves a command parameter of the given type using the registered argument resolver,
     * advancing the supplied argument index holder in place based on the resolver's width.
     *
     * <p>This centralizes the per-parameter resolution logic so generated command wrappers
     * can delegate to the manager instead of duplicating resolver lookup, width tracking,
     * optional/default handling, and missing-argument reporting.
     *
     * @param sender       the command sender as observed by the wrapper. Must be an instance of this
     *                     manager's sender type {@code S}; the wrapper is responsible for passing
     *                     the platform-native sender (e.g. {@code CommandSourceStack} for Paper),
     *                     not whatever sender type the user-facing command method declares.
     * @param type         the target class type to resolve
     * @param args         the full command arguments array available to the resolver
     * @param indexHolder  a single-element array holding the current argument index; updated in place
     * @param paramName    the parameter name used in missing-argument error messages
     * @param optional     whether the parameter is optional
     * @param defaultValue the default value string to use when optional and missing
     * @param <T>          the target parameter type
     * @return the resolved parameter value, or {@code null} when optional and missing with no default
     * @throws Exception        if the registered resolver throws during resolution
     * @throws CommandException when a required parameter has insufficient arguments
     */
    public <T> T resolveParameter(S sender, Class<T> type, String[] args,
                                  int[] indexHolder, String paramName, boolean optional, String defaultValue)
            throws Exception {
        ArgumentResolver<S, T> resolver = getResolver(type);
        int width = resolver.getWidth();
        int argIdx = indexHolder[0];

        if (argIdx + width > args.length) {
            if (optional) {
                if (defaultValue == null) {
                    return null;
                }
                return resolver.resolve(sender, new String[]{defaultValue}, defaultValue);
            }
            throw new CommandException(formatMessage(
                    "missing-argument",
                    "Missing arguments for parameter: %s",
                    paramName));
        }

        T result;
        if (width == 1) {
            String argStr = args[argIdx];
            result = argStr == null ? null : resolver.resolve(sender, args, argStr);
            indexHolder[0] = argIdx + 1;
        } else {
            String[] subArgs = Arrays.copyOfRange(args, argIdx, argIdx + width);
            result = resolver.resolve(sender, subArgs, subArgs[subArgs.length - 1]);
            indexHolder[0] = argIdx + width;
        }
        return result;
    }

    /**
     * Gets the registered argument resolver for the given class type.
     * Supports exact match, class hierarchy (superclass/interface) match, dynamic providers, and a sender fallback.
     *
     * @param type the class type
     * @param <T>  the parameter type
     * @return the resolver
     */
    @SuppressWarnings("unchecked")
    public <T> ArgumentResolver<S, T> getResolver(Class<T> type) {
        // 1. Exact match
        ArgumentResolver<S, ?> resolver = resolvers.get(type);
        if (resolver != null) {
            return (ArgumentResolver<S, T>) resolver;
        }

        // 2. Class hierarchy match (interfaces / superclasses)
        for (Map.Entry<Class<?>, ArgumentResolver<S, ?>> entry : resolvers.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) {
                return (ArgumentResolver<S, T>) entry.getValue();
            }
        }

        // 3. Dynamic providers match
        for (ArgumentResolverProvider<S> provider : providers) {
            ArgumentResolver<S, ?> dynamicResolver = provider.getResolver(type);
            if (dynamicResolver != null) {
                return (ArgumentResolver<S, T>) dynamicResolver;
            }
        }

        // 4. Default fallback for sender-assignable types
        return (sender, args, current) -> {
            if (type.isInstance(sender)) {
                return type.cast(sender);
            }
            throw new IllegalArgumentException("No argument resolver registered for type: " + type.getName() + " and sender is not an instance of it.");
        };
    }

    /**
     * Gets the error handler.
     *
     * @return the error handler
     */
    public ErrorHandler<S> getErrorHandler() {
        return errorHandler;
    }

    /**
     * Sets the error handler.
     *
     * @param errorHandler the error handler to set
     */
    public void setErrorHandler(ErrorHandler<S> errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     * Registers a CommandInfoExposer for a command instance.
     *
     * @param commandInstance the command instance
     * @param exposer         the exposer
     */
    public void registerExposer(Object commandInstance, CommandInfoExposer exposer) {
        exposers.put(commandInstance, exposer);
    }

    /**
     * Gets the list of command information for the given command instance.
     *
     * @param commandInstance the command instance
     * @return the list of command information, or an empty list if not registered
     */
    public List<CommandInfo> getCommandInfo(Object commandInstance) {
        CommandInfoExposer exposer = exposers.get(commandInstance);
        if (exposer == null) {
            return Collections.emptyList();
        }
        return exposer.getCommandInfo();
    }
}
