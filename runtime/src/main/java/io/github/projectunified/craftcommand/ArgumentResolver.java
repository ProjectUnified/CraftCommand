package io.github.projectunified.craftcommand;

import java.util.List;

/**
 * Resolver for command parameter arguments.
 * Converts string command arguments into Java types and provides tab-completion suggestions.
 *
 * @param <S> the type of the sender
 * @param <T> the target type of the resolved parameter
 */
public interface ArgumentResolver<S, T> {
    /**
     * Resolves the parameter value from the given arguments.
     *
     * @param sender  the sender executing the command
     * @param args    the complete list of arguments passed to the command
     * @param current the current argument string being resolved
     * @return the resolved parameter value
     * @throws Exception if resolution fails
     */
    T resolve(S sender, String[] args, String current) throws Exception;

    /**
     * Provides a list of tab-completion suggestions for this parameter.
     *
     * @param sender  the sender tab-completing the command
     * @param args    the complete list of arguments entered so far
     * @param current the current argument string being completed
     * @return a list of suggestions
     */
    default List<String> suggest(S sender, String[] args, String current) {
        return java.util.Collections.emptyList();
    }

    /**
     * The number of arguments this resolver consumes.
     * Defaults to 1.
     *
     * @return the width of the resolver
     */
    default int getWidth() {
        return 1;
    }
}
