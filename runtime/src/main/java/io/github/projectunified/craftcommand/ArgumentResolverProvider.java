package io.github.projectunified.craftcommand;

/**
 * Provides dynamic argument resolvers for types not registered directly.
 *
 * @param <S> sender type
 */
public interface ArgumentResolverProvider<S> {
    /**
     * Returns a resolver for the type, or null if not supported.
     */
    ArgumentResolver<S, ?> getResolver(Class<?> type);
}
