package io.github.projectunified.craftcommand.processor.parser;

import io.github.projectunified.craftcommand.processor.TypeNames;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeMirror;

/**
 * Compile-time element lookups for resolver methods, suggest methods, and fields.
 */
public final class ResolverLookup {

    private ResolverLookup() {
    }

    /**
     * Find a method by name on the given type element.
     * Searches the current class and all enclosing classes.
     *
     * @param typeElement the type element to search
     * @param name        the method name
     * @return the matching ExecutableElement, or null if not found
     */
    public static ExecutableElement findMethod(TypeElement typeElement, String name) {
        TypeElement current = typeElement;
        while (current != null) {
            for (Element enclosed : current.getEnclosedElements()) {
                if (enclosed instanceof ExecutableElement method && method.getSimpleName().contentEquals(name)) {
                    return method;
                }
            }
            current = current.getEnclosingElement() instanceof TypeElement type ? type : null;
        }
        return null;
    }

    /**
     * Find a suggest method by name and validate its signature.
     *
     * <p>Valid signatures (where S is a sender type):
     * <ul>
     *   <li>{@code Collection<String> m()}</li>
     *   <li>{@code Collection<String> m(String[] current)}</li>
     *   <li>{@code Collection<String> m(SenderType sender)} — a single non-{@code String[]} argument is treated
     *       as the sender by the generators</li>
     *   <li>{@code Collection<String> m(String[] current, String[] context)}</li>
     *   <li>{@code Collection<String> m(S sender, String[] current)}</li>
     *   <li>{@code Collection<String> m(S sender, String[] current, String[] context)}</li>
     * </ul>
     *
     * @param typeElement the type element to search
     * @param name        the method name
     * @return the matching method, or {@code null} if not found or invalid signature
     */
    public static ExecutableElement findSuggestMethod(TypeElement typeElement, String name) {
        ExecutableElement method = findMethod(typeElement, name);
        return method != null && isValidSuggestMethod(method) ? method : null;
    }

    private static boolean isValidSuggestMethod(ExecutableElement method) {
        if (!isCollectionOfStrings(method.getReturnType())) return false;

        var params = method.getParameters();
        return switch (params.size()) {
            case 0, 1 -> true;
            case 2 -> TypeNames.isStringArray(params.get(1).asType());
            case 3 -> TypeNames.isStringArray(params.get(1).asType())
                    && TypeNames.isStringArray(params.get(2).asType());
            default -> false;
        };
    }

    private static boolean isCollectionOfStrings(TypeMirror type) {
        if (!(type instanceof DeclaredType declaredType)) return false;
        var typeName = ((TypeElement) declaredType.asElement()).getQualifiedName().toString();
        boolean isCollection = switch (typeName) {
            case "java.util.Collection", "java.util.List", "java.util.ArrayList",
                 "java.util.Set", "java.util.HashSet", "java.util.LinkedList" -> true;
            default -> false;
        };
        var typeArgs = declaredType.getTypeArguments();
        return isCollection && typeArgs.size() == 1
                && typeArgs.get(0) instanceof DeclaredType declared
                && ((TypeElement) declared.asElement()).getQualifiedName().contentEquals("java.lang.String");
    }

    /**
     * Checks if a type element declares a field with the given name.
     *
     * @param typeElement the type element
     * @param name        the field name
     * @return true if the type element declares a field with the given name.
     */
    public static boolean isField(TypeElement typeElement, String name) {
        return typeElement.getEnclosedElements().stream()
                .anyMatch(enclosed -> enclosed.getKind().isField() && enclosed.getSimpleName().contentEquals(name));
    }
}
