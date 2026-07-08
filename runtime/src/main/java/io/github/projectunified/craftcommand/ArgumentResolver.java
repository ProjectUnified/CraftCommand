package io.github.projectunified.craftcommand;

import java.util.List;

/**
 * Converts string arguments into typed values and provides tab-completion suggestions.
 *
 * @param <S> sender type
 * @param <T> resolved parameter type
 */
public interface ArgumentResolver<S, T> {
    /**
     * Resolves a parameter value from the current argument.
     */
    T resolve(S sender, String[] args, String current) throws Exception;

    /**
     * Returns tab-completion suggestions. Default: empty.
     */
    default List<String> suggest(S sender, String[] args, String current) {
        return java.util.Collections.emptyList();
    }

    /**
     * Number of arguments this resolver consumes. Default: 1.
     */
    default int getWidth() {
        return 1;
    }
}
