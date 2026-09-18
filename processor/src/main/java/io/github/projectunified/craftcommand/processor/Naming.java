package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;

/**
 * Pure naming utilities for generated fields, methods, and identifiers.
 *
 * <p>Centralizes the naming conventions used across the code generator so that the same logical concept always
 * maps to the same generated name, and so generated members read as if they had been written by hand. All
 * methods are static and side-effect free.
 */
public final class Naming {

    private Naming() {
    }

    /**
     * Gets the simple name of a type name.
     *
     * @param typeName the type name
     * @return the simple name of a {@link TypeName} (text after the last dot).
     */
    public static String simpleName(TypeName typeName) {
        String name = typeName.toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot == -1 ? name : name.substring(lastDot + 1);
    }

    /**
     * Gets the field name holding a nested subcommand class instance.
     *
     * @param nestedClass the class name of the nested command
     * @return the field name, for example {@code subInstanceOuterInner} for class {@code Outer.Inner}
     */
    public static String subcommandField(ClassName nestedClass) {
        return "subInstance" + typePath(nestedClass);
    }

    /**
     * Gets the helper method name for a parameter's suggestions.
     *
     * @param commandClass    the command class being generated
     * @param methodOrDefault the subcommand name, or {@code "default"} for the default action
     * @param paramIndex      the zero-based parameter index
     * @return the helper method name, for example {@code suggestCalculatorAdd_0}
     */
    public static String suggestMethod(ClassName commandClass, String methodOrDefault, int paramIndex) {
        return "suggest" + typePath(commandClass) + camelCase(methodOrDefault) + "_" + paramIndex;
    }

    /**
     * Gets the execution helper method name for a nested subcommand class.
     *
     * @param nestedClass the nested class name
     * @return the helper that executes the nested command, for example {@code executeOuterInner}
     */
    public static String executeHelper(ClassName nestedClass) {
        return "execute" + typePath(nestedClass);
    }

    /**
     * Gets the suggestion routing helper method name for a nested subcommand class.
     *
     * @param nestedClass the nested class name
     * @return the helper that routes tab completion into the nested command, for example
     * {@code suggestOuterInner}
     */
    public static String suggestHelper(ClassName nestedClass) {
        return "suggest" + typePath(nestedClass);
    }

    /**
     * Sanitizes a string into a valid Java identifier.
     *
     * @param name the input name
     * @return a valid Java identifier derived from an arbitrary command name
     * (replaces {@code -} and spaces with {@code _}).
     */
    public static String sanitizeIdentifier(String name) {
        return name.replace("-", "_").replace(" ", "_");
    }

    /**
     * The concatenated simple names of a class, which keeps generated members unique across nested classes.
     *
     * @param className the class name
     * @return the class path as a single upper camel case identifier
     */
    private static String typePath(ClassName className) {
        StringBuilder path = new StringBuilder();
        for (String simpleName : className.simpleNames()) {
            path.append(camelCase(simpleName));
        }
        return path.toString();
    }

    /**
     * Converts an arbitrary name to upper camel case, keeping it a valid identifier.
     *
     * @param name the input name
     * @return the upper camel case form, for example {@code string-with-default} to
     * {@code StringWithDefault}
     */
    private static String camelCase(String name) {
        StringBuilder result = new StringBuilder();
        boolean capitalize = true;
        for (int i = 0; i < name.length(); i++) {
            char c = name.charAt(i);
            if (!Character.isLetterOrDigit(c)) {
                capitalize = true;
                continue;
            }
            result.append(capitalize ? Character.toUpperCase(c) : c);
            capitalize = false;
        }
        return result.toString();
    }
}
