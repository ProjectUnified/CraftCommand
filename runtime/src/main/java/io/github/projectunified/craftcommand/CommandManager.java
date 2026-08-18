package io.github.projectunified.craftcommand;

import io.github.projectunified.craftcommand.exception.CommandException;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodType;
import java.util.*;
import java.util.function.BiConsumer;
import java.util.function.Function;

/**
 * Base command manager. Handles resolver registration, message formatting, wrapper instantiation, and error handling.
 *
 * @param <S> sender type
 */
public abstract class CommandManager<S> {
    private final Map<Class<?>, ArgumentResolver<S, ?>> resolvers = new HashMap<>();
    private final List<Function<Class<?>, ArgumentResolver<S, ?>>> providers = new ArrayList<>();
    private final Map<Class<?>, Function<S, ?>> senderResolvers = new HashMap<>();
    private final Map<Object, BaseCommand> wrappers = new HashMap<>();
    private BiConsumer<S, Exception> errorHandler;

    public CommandManager(BiConsumer<S, Exception> errorHandler) {
        this.errorHandler = errorHandler;
    }

    /**
     * Filters suggestions by prefix (case-insensitive).
     */
    public static List<String> filterSuggestions(Collection<String> suggestions, String current) {
        if (suggestions == null || suggestions.isEmpty()) return Collections.emptyList();
        List<String> result = new ArrayList<>();
        String lower = (current == null || current.isEmpty()) ? null : current.toLowerCase();
        for (String s : suggestions) {
            if (s != null && (lower == null || s.toLowerCase().startsWith(lower))) {
                result.add(s);
            }
        }
        return result;
    }

    /**
     * Registers a command.
     */
    public abstract void register(Object command);

    /**
     * Helper to instantiate a generated command wrapper using MethodHandles.
     *
     * @param commandInstance  the original command instance
     * @param suffix           the wrapper class suffix (e.g. "$StandaloneCommand")
     * @param wrapperClassType the expected wrapper class or interface
     * @param <W>              the wrapper type
     * @return the instantiated wrapper
     * @throws Throwable if reflection or instantiation fails
     */
    protected <W> W instantiateWrapper(Object commandInstance, String suffix, Class<W> wrapperClassType) throws Throwable {
        Class<?> commandClass = commandInstance.getClass();
        Class<?> wrapperClass = Class.forName(commandClass.getName() + suffix);
        MethodHandle handle = MethodHandles.lookup().findConstructor(
                wrapperClass,
                MethodType.methodType(void.class, commandClass, CommandManager.class)
        );
        Object wrapper = handle.invoke(commandInstance, this);
        return wrapperClassType.cast(wrapper);
    }

    /**
     * Associates a command instance with its generated wrapper.
     *
     * @param commandInstance the original command instance
     * @param wrapper         the generated command wrapper
     */
    protected void registerWrapper(Object commandInstance, BaseCommand wrapper) {
        this.wrappers.put(commandInstance, wrapper);
    }

    /**
     * Gets command metadata for a registered command instance.
     *
     * @param commandInstance the original command instance passed to {@link #register(Object)}
     * @return the command info list, or empty list if not found
     */
    public List<CommandInfo> getCommandInfo(Object commandInstance) {
        BaseCommand wrapper = wrappers.get(commandInstance);
        if (wrapper != null) {
            return wrapper.getCommandInfo();
        }
        return Collections.emptyList();
    }

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
    public void registerProvider(Function<Class<?>, ArgumentResolver<S, ?>> provider) {
        providers.add(provider);
    }

    /**
     * Registers a global sender resolver.
     *
     * @param type     the target sender type
     * @param resolver function that converts the base sender to the target type
     */
    public <T> void registerSenderResolver(Class<T> type, Function<S, T> resolver) {
        senderResolvers.put(type, resolver);
    }

    /**
     * Resolves the sender to the target type using registered sender resolvers.
     *
     * @param type   the target sender type
     * @param sender the base sender
     * @return the resolved sender, or {@code null} if no resolver is registered
     */
    @SuppressWarnings("unchecked")
    public <T> T resolveSender(Class<T> type, S sender) {
        Function<S, ?> resolver = senderResolvers.get(type);
        if (resolver != null) return (T) resolver.apply(sender);
        return null;
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
                String[] defaultCurrent = new String[]{defaultValue};
                return resolver.resolve(sender, defaultCurrent, args);
            }
            throw new CommandException(formatMessage("missing-argument", "Missing arguments for parameter: %s", paramName));
        }

        String[] current = Arrays.copyOfRange(args, argIdx, argIdx + width);
        T result = resolver.resolve(sender, current, args);
        indexHolder[0] = argIdx + width;
        return result;
    }

    /**
     * Checks if a resolver is registered or provided for the given type.
     *
     * @param type the type to check
     * @return {@code true} if a resolver is available
     */
    public boolean hasResolver(Class<?> type) {
        if (resolvers.containsKey(type)) return true;
        for (Class<?> key : resolvers.keySet()) {
            if (key.isAssignableFrom(type)) return true;
        }
        for (Function<Class<?>, ArgumentResolver<S, ?>> provider : providers) {
            if (provider.apply(type) != null) return true;
        }
        return false;
    }

    /**
     * Gets the resolver for a type. Checks exact match, hierarchy, and providers.
     *
     * @throws IllegalArgumentException if no resolver is registered for the type
     */
    @SuppressWarnings("unchecked")
    public <T> ArgumentResolver<S, T> getResolver(Class<T> type) {
        ArgumentResolver<S, ?> resolver = resolvers.get(type);
        if (resolver != null) return (ArgumentResolver<S, T>) resolver;

        for (Map.Entry<Class<?>, ArgumentResolver<S, ?>> entry : resolvers.entrySet()) {
            if (entry.getKey().isAssignableFrom(type)) return (ArgumentResolver<S, T>) entry.getValue();
        }

        for (Function<Class<?>, ArgumentResolver<S, ?>> provider : providers) {
            ArgumentResolver<S, ?> dynamicResolver = provider.apply(type);
            if (dynamicResolver != null) return (ArgumentResolver<S, T>) dynamicResolver;
        }

        throw new IllegalArgumentException("No resolver registered for type: " + type.getName());
    }

    public BiConsumer<S, Exception> getErrorHandler() {
        return errorHandler;
    }

    public void setErrorHandler(BiConsumer<S, Exception> errorHandler) {
        this.errorHandler = errorHandler;
    }
}
