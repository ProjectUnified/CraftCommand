package io.github.projectunified.craftcommand;

/**
 * A provider for dynamic argument resolvers.
 * Useful for registering resolvers for entire class hierarchies or generic types.
 *
 * @param <S> the command sender type
 */
public interface ArgumentResolverProvider<S> {

    /**
     * Gets a resolver for the specified type.
     *
     * @param type the type to resolve
     * @return the argument resolver, or {@code null} if this provider cannot resolve the type
     */
    ArgumentResolver<S, ?> getResolver(Class<?> type);
}
