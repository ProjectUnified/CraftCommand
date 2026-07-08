package io.github.projectunified.craftcommand.processor.extension;

import com.palantir.javapoet.TypeName;

import java.util.Collection;
import java.util.List;

/**
 * SPI provider that supplies global tab-completion suggestions for parameter types.
 *
 * <p>When a command parameter has no {@code @Suggest} annotation and is not a built-in type,
 * the processor checks registered {@link SuggestionProvider} instances for matching types.
 *
 * <p>This eliminates the need to write {@code @Suggest} methods for commonly used custom types
 * like material keys, player names, etc.
 */
public interface SuggestionProvider {

    /**
     * Returns all type names this provider can suggest for.
     *
     * @return collection of fully qualified type names
     */
    Collection<String> supportedTypes();

    /**
     * Generates a list of suggestions for the given type.
     *
     * @param type    the parameter type
     * @param sender  the variable name of the command sender in generated code
     * @param args    the variable name of the args array in generated code
     * @param current the variable name of the current input string in generated code
     * @return a list of suggestion strings, or {@code null} if no suggestions available
     */
    List<String> suggest(TypeName type, String sender, String args, String current);
}
