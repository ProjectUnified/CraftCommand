package io.github.projectunified.craftcommand.processor;

import io.github.projectunified.craftcommand.annotation.Resolve;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.model.ParameterModel;

import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.element.VariableElement;
import javax.lang.model.type.ArrayType;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Compile-time lookups for local resolvers, suggest methods, fields, and
 * command-model class resolution.
 */
public final class ResolverLookup {

    private ResolverLookup() {
    }

    /**
     * Find a no-arg/instance method by name on the given type element.
     * Searches the current class and all parent classes.
     *
     * @param typeElement the type element to search
     * @param name        the method name
     * @return the matching ExecutableElement, or null if not found
     */
    public static ExecutableElement findMethod(TypeElement typeElement, String name) {
        TypeElement current = typeElement;
        while (current != null) {
            for (Element enclosed : current.getEnclosedElements()) {
                if (enclosed instanceof ExecutableElement
                        && enclosed.getSimpleName().toString().equals(name)) {
                    return (ExecutableElement) enclosed;
                }
            }
            Element enclosing = current.getEnclosingElement();
            current = (enclosing instanceof TypeElement) ? (TypeElement) enclosing : null;
        }
        return null;
    }

    /**
     * Find a local {@code @Resolve} method for the given parameter.
     *
     * <p>If the parameter carries an explicit {@code @Resolve("name")}, the
     * method is matched by name. Implicit resolution by return type is no longer
     * supported — use explicit {@code @Resolve("name")} instead.
     *
     * @param classModel the command class where resolution starts
     * @param p          the parameter to resolve
     * @return the matching resolver method, or {@code null} if none
     */
    public static ExecutableElement findLocalResolver(CommandModel classModel, ParameterModel p) {
        Resolve resolveAnn = p.getElement().getAnnotation(Resolve.class);
        if (resolveAnn == null || resolveAnn.value().isEmpty()) {
            return null;
        }

        String explicitName = resolveAnn.value();
        TypeElement current = classModel.getElement();
        while (current != null) {
            for (Element enclosed : current.getEnclosedElements()) {
                if (!(enclosed instanceof ExecutableElement)) continue;
                ExecutableElement method = (ExecutableElement) enclosed;
                if (method.getSimpleName().toString().equals(explicitName)) {
                    return method;
                }
            }
            Element enclosing = current.getEnclosingElement();
            current = (enclosing instanceof TypeElement) ? (TypeElement) enclosing : null;
        }
        return null;
    }

    /**
     * Walk the command tree to find the {@link CommandModel} whose element
     * equals {@code targetClass}.
     *
     * @param current     the root or current command model to search from
     * @param targetClass the target class element
     * @return the matching CommandModel, or null if not found
     */
    public static CommandModel findModelForClass(CommandModel current, TypeElement targetClass) {
        if (current.getElement().equals(targetClass)) {
            return current;
        }
        for (CommandModel child : current.getNestedSubcommands()) {
            CommandModel found = findModelForClass(child, targetClass);
            if (found != null) {
                return found;
            }
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
        if (method == null || !isValidSuggestMethod(method)) {
            return null;
        }
        return method;
    }

    /**
     * Validates that a method has a valid suggest method signature.
     *
     * <p>Valid signatures:
     * <ul>
     *   <li>{@code Collection<String> m()}</li>
     *   <li>{@code Collection<String> m(SenderType sender)}</li>
     *   <li>{@code Collection<String> m(String[] current)}</li>
     *   <li>{@code Collection<String> m(String[] current, String[] context)}</li>
     *   <li>{@code Collection<String> m(SenderType sender, String[] current)}</li>
     *   <li>{@code Collection<String> m(SenderType sender, String[] current, String[] context)}</li>
     * </ul>
     */
    private static boolean isValidSuggestMethod(ExecutableElement method) {
        TypeMirror returnType = method.getReturnType();
        if (!isCollectionOfStrings(returnType)) {
            return false;
        }

        List<? extends VariableElement> params = method.getParameters();
        int paramCount = params.size();

        // Valid param counts: 0, 1, 2, or 3
        if (paramCount < 0 || paramCount > 3) {
            return false;
        }

        if (paramCount == 0) {
            // m() — no parameters
            return true;
        }

        if (paramCount == 1) {
            // Can be m(SenderType) or m(String[] current)
            return !isStringArray(params.get(0).asType()) || true; // Valid either way
        }

        if (paramCount == 2) {
            // Can be m(SenderType, String[] current) or m(String[] current, String[] context)
            VariableElement p0 = params.get(0);
            VariableElement p1 = params.get(1);

            // Case A: m(String[] current, String[] context)
            if (isStringArray(p0.asType()) && isStringArray(p1.asType())) {
                return true;
            }

            // Case B: m(SenderType, String[] current)
            return isStringArray(p1.asType());
        }

        // paramCount == 3: m(SenderType, String[] current, String[] context)
        VariableElement p1 = params.get(1);
        VariableElement p2 = params.get(2);
        return isStringArray(p1.asType()) && isStringArray(p2.asType());
    }

    private static boolean isCollectionOfStrings(TypeMirror type) {
        if (type.getKind() != TypeKind.DECLARED) return false;
        DeclaredType declaredType = (DeclaredType) type;
        TypeElement typeElement = (TypeElement) declaredType.asElement();
        String typeName = typeElement.getQualifiedName().toString();

        // Check if it's java.util.Collection or a subtype
        if (!typeName.equals("java.util.Collection")
                && !typeName.equals("java.util.List")
                && !typeName.equals("java.util.ArrayList")
                && !typeName.equals("java.util.Set")
                && !typeName.equals("java.util.HashSet")
                && !typeName.equals("java.util.LinkedList")) {
            return false;
        }

        // Check type argument is String
        List<? extends TypeMirror> typeArgs = declaredType.getTypeArguments();
        if (typeArgs.size() != 1) return false;

        TypeMirror typeArg = typeArgs.get(0);
        if (typeArg.getKind() != TypeKind.DECLARED) return false;
        TypeElement typeArgElement = (TypeElement) ((DeclaredType) typeArg).asElement();
        return typeArgElement.getQualifiedName().toString().equals("java.lang.String");
    }

    private static boolean isStringArray(TypeMirror type) {
        if (type.getKind() != TypeKind.ARRAY) return false;
        ArrayType arrayType = (ArrayType) type;
        TypeMirror componentType = arrayType.getComponentType();
        if (componentType.getKind() != TypeKind.DECLARED) return false;
        TypeElement componentElement = (TypeElement) ((DeclaredType) componentType).asElement();
        return componentElement.getQualifiedName().toString().equals("java.lang.String");
    }

    private static boolean isStringOrStringArray(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
            return typeElement.getQualifiedName().toString().equals("java.lang.String");
        }
        return isStringArray(type);
    }

    /**
     * Checks if a type element declares a field with the given name.
     *
     * @param typeElement the type element
     * @param name        the field name
     * @return true if the type element declares a field with the given name.
     */
    public static boolean isField(TypeElement typeElement, String name) {
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind().isField() && enclosed.getSimpleName().toString().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
