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
     *
     * @param sender  the command sender
     * @param current the argument array slice for this parameter type
     * @param context the full command argument array
     * @return the resolved value
     */
    T resolve(S sender, String[] current, String[] context) throws Exception;

    /**
     * Returns tab-completion suggestions. Default: empty.
     *
     * @param sender  the command sender
     * @param current the argument array slice for this parameter type
     * @param context the full command argument array
     * @return the list of suggestions
     */
    default List<String> suggest(S sender, String[] current, String[] context) {
        return java.util.Collections.emptyList();
    }

    /**
     * Number of arguments this resolver consumes. Default: 1.
     */
    default int getWidth() {
        return 1;
    }
}
