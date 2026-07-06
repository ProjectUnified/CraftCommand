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
    private final Map<String, String> messages = new HashMap<>();
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
     * Sets a custom error message for a given key.
     *
     * @param key     the message key
     * @param message the message template
     */
    public void setMessage(String key, String message) {
        messages.put(key, message);
    }

    /**
     * Sets custom error messages in bulk.
     *
     * @param messages a map of message keys to templates
     */
    public void setMessages(Map<String, String> messages) {
        this.messages.putAll(messages);
    }

    /**
     * Gets the raw message template for a key.
     *
     * @param key the message key
     * @return the message template, or {@code null} if not set
     */
    public String getMessage(String key) {
        return messages.get(key);
    }

    /**
     * Formats a message template for a key with the given arguments.
     * If the key is not set, falls back to the defaultValue.
     *
     * @param key          the message key
     * @param defaultValue the default template to use if the key is not set
     * @param args         the arguments to format the template with
     * @return the formatted message
     */
    public String formatMessage(String key, String defaultValue, Object... args) {
        String template = messages.get(key);
        if (template == null) {
            template = defaultValue;
        }
        try {
            return String.format(template, args);
        } catch (Exception e) {
            return template;
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


}
