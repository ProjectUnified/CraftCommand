package io.github.projectunified.craftcommand;

import io.github.projectunified.craftcommand.exception.CommandException;

import java.util.*;

/**
 * Base command manager. Handles resolver registration, message formatting, and error handling.
 *
 * @param <S> sender type
 */
public abstract class CommandManager<S> {
    private final Map<Class<?>, ArgumentResolver<S, ?>> resolvers = new HashMap<>();
    private final List<ArgumentResolverProvider<S>> providers = new ArrayList<>();
    private final Map<Object, CommandInfoExposer> exposers = new java.util.concurrent.ConcurrentHashMap<>();
    private ErrorHandler<S> errorHandler;

    public CommandManager(ErrorHandler<S> errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     * Filters suggestions by prefix (case-insensitive).
     */
    public static List<String> filterSuggestions(List<String> suggestions, String current) {
        if (suggestions == null) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        String lower = current.toLowerCase();
        for (String s : suggestions) {
            if (s.toLowerCase().startsWith(lower)) result.add(s);
        }
        return result;
    }

    /**
     * Registers a command. Platform-specific.
     */
    public abstract void register(Object command);

    /**
     * Formats a message by key. Override for i18n.
     * Default: {@code String.format(defaultValue, args)}.
     */
    public String formatMessage(String key, String defaultValue, Object... args) {
        try {
            return String.format(defaultValue, args);
        } catch (Exception e) {
            return defaultValue;
        }
    }

    /**
     * Registers a resolver for a specific type.
     */
    public <T> void registerResolver(Class<T> type, ArgumentResolver<S, T> resolver) {
        resolvers.put(type, resolver);
    }

    /**
     * Registers a dynamic resolver provider.
     */
    public void registerProvider(ArgumentResolverProvider<S> provider) {
        providers.add(provider);
    }

    /**
     * Resolves a parameter, advancing the index holder. Used by generated wrappers.
     *
     * @throws CommandException when a required parameter is missing
     */
    public <T> T resolveParameter(S sender, Class<T> type, String[] args,
                                  int[] indexHolder, String paramName, boolean optional, String defaultValue)
            throws Exception {
        ArgumentResolver<S, T> resolver = getResolver(type);
        int width = resolver.getWidth();
        int argIdx = indexHolder[0];

        if (argIdx + width > args.length) {
            if (optional) {
                if (defaultValue == null) return null;
                return resolver.resolve(sender, new String[]{defaultValue}, defaultValue);
            }
            throw new CommandException(formatMessage("missing-argument", "Missing arguments for parameter: %s", paramName));
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
     * Gets the resolver for a type. Checks exact match, hierarchy, providers, then sender fallback.
     */
    @SuppressWarnings("unchecked")
    public <T> ArgumentResolver<S, T> getResolver(Class<T> type) {
        ArgumentResolver<S, ?> resolver = resolvers.get(type);
        if (resolver != null) return (ArgumentResolver<S, T>) resolver;

        for (Map.Entry<Class<?>, ArgumentResolver<S, ?>> entry : resolvers.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) return (ArgumentResolver<S, T>) entry.getValue();
        }

        for (ArgumentResolverProvider<S> provider : providers) {
            ArgumentResolver<S, ?> dynamicResolver = provider.getResolver(type);
            if (dynamicResolver != null) return (ArgumentResolver<S, T>) dynamicResolver;
        }

        return (sender, args, current) -> {
            if (type.isInstance(sender)) return type.cast(sender);
            throw new IllegalArgumentException("No resolver for type: " + type.getName());
        };
    }

    public ErrorHandler<S> getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(ErrorHandler<S> errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     * Stores command metadata for later retrieval via {@link #getCommandInfo(Object)}.
     */
    public void registerExposer(Object commandInstance, CommandInfoExposer exposer) {
        exposers.put(commandInstance, exposer);
    }

    /**
     * Gets command metadata, or empty list if not registered.
     */
    public List<CommandInfo> getCommandInfo(Object commandInstance) {
        CommandInfoExposer exposer = exposers.get(commandInstance);
        return exposer != null ? exposer.getCommandInfo() : Collections.emptyList();
    }
}
