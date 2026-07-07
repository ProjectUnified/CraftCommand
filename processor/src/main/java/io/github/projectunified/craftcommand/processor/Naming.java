package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;

/**
 * Pure naming utilities for generated fields, methods, and identifiers.
 *
 * <p>Centralizes the naming conventions used across the code generator so that
 * the same logical concept always maps to the same generated name. All methods
 * are static and side-effect free.
 */
public final class Naming {

    private Naming() {
    }

    /**
     * @return the simple name of a {@link TypeName} (text after the last dot).
     */
    public static String simpleName(TypeName typeName) {
        String name = typeName.toString();
        int lastDot = name.lastIndexOf('.');
        return lastDot == -1 ? name : name.substring(lastDot + 1);
    }

    /**
     * @return the field name for a nested subcommand class instance, e.g.
     * {@code subInstance_outer_inner} for class {@code Outer.Inner}.
     */
    public static String subcommandField(ClassName nestedClass) {
        return "subInstance_" + String.join("_", nestedClass.simpleNames()).toLowerCase();
    }

    /**
     * @return the helper method name for resolving a dynamic (global-resolver)
     * parameter type, e.g. {@code resolve_com_example_Foo}.
     */
    public static String resolverMethod(TypeName type) {
        if (type instanceof ClassName) {
            return "resolve_" + String.join("_", ((ClassName) type).simpleNames());
        }
        return "resolve_" + type.toString().replace(".", "_").replace("$", "_");
    }

    /**
     * @param classModelPath  the lowercased dot-joined class simple-names
     * @param methodOrDefault the subcommand name, or {@code "default"} for the default action
     * @param paramIndex      the zero-based parameter index
     * @return the helper method name for a parameter's suggestion provider.
     */
    public static String suggestMethod(String classModelPath, String methodOrDefault, int paramIndex) {
        String sanitized = methodOrDefault.replaceAll("[^a-zA-Z0-9_]", "_");
        return "suggest_" + classModelPath + "_" + sanitized + "_" + paramIndex;
    }

    /**
     * @return the lowercased dot-joined simple-names of a class, used as a
     * disambiguating prefix for generated identifiers.
     */
    public static String classPath(ClassName className) {
        return String.join("_", className.simpleNames()).toLowerCase();
    }

    /**
     * @return {@code "execute_" + lowercased class path} — the routing helper
     * for a nested subcommand class.
     */
    public static String executeHelper(ClassName nestedClass) {
        return "execute_" + classPath(nestedClass);
    }

    /**
     * @return {@code "suggest_" + lowercased class path} — the suggestion
     * routing helper for a nested subcommand class.
     */
    public static String suggestHelper(ClassName nestedClass) {
        return "suggest_" + classPath(nestedClass);
    }

    /**
     * @return a valid Java identifier derived from an arbitrary command name
     * (replaces {@code -} and spaces with {@code _}).
     */
    public static String sanitizeIdentifier(String name) {
        return name.replace("-", "_").replace(" ", "_");
    }
}
