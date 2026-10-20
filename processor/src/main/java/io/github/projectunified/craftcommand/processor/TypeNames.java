package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.ArrayTypeName;
import com.palantir.javapoet.ClassName;
import com.palantir.javapoet.TypeName;

import javax.lang.model.type.TypeMirror;

/**
 * Compile-time type predicates shared by the parser and the generators.
 */
public final class TypeNames {
    private TypeNames() {
    }

    /**
     * @return true for {@code java.lang.String[]}.
     */
    public static boolean isStringArray(TypeMirror type) {
        TypeName typeName = TypeName.get(type);
        return typeName instanceof ArrayTypeName
                && ((ArrayTypeName) typeName).componentType().equals(ClassName.get(String.class));
    }
}
