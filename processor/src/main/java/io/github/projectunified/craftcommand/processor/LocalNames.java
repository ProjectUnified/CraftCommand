package io.github.projectunified.craftcommand.processor;

import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import java.util.Arrays;
import java.util.HashSet;
import java.util.Set;

/**
 * Allocates the local variable names of one generated command slot.
 *
 * <p>Names are derived from the parameters the user declared, so generated code reads {@code num1} and
 * {@code num2} rather than {@code param_0} and {@code param_1}. Names that would shadow a local the generated
 * code itself relies on are given a unique suffix instead.
 */
public final class LocalNames {
    private static final Set<String> RESERVED = new HashSet<>(Arrays.asList(
            "sender", "senderCast", "args", "sub", "subArgs", "argIdx", "argIdxHolder",
            "instance", "manager", "e", "list", "current", "currentStr", "index", "tempIdx",
            "ctx", "sb", "i", "j"));

    private final Set<String> used = new HashSet<>(RESERVED);

    /**
     * The identifier a parameter was declared with in the command class.
     *
     * @param parameter the parameter model
     * @return the declared identifier, or {@code null} when the parameter has no element
     */
    public static String declaredName(ParameterModel parameter) {
        return parameter.getElement() == null ? null : parameter.getElement().getSimpleName().toString();
    }

    /**
     * Allocates a local for a parameter, based on the name the user declared for it.
     *
     * @param preferred the declared name, or {@code null} to use the fallback
     * @param fallback  the name to use when the declared name is unusable
     * @return a name that is unique within the generated method
     */
    public String allocate(String preferred, String fallback) {
        if (preferred == null || preferred.isEmpty() || RESERVED.contains(preferred)) {
            return unique(fallback);
        }
        return unique(preferred);
    }

    private String unique(String name) {
        String candidate = name;
        int suffix = 2;
        while (!used.add(candidate)) {
            candidate = name + "_" + suffix++;
        }
        return candidate;
    }
}
