package io.github.projectunified.craftcommand;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
            return java.util.Collections.emptyList();
        }
        return exposer.getCommandInfo();
    }
}
