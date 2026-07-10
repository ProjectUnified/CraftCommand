package io.github.projectunified.craftcommand.processor;

import io.github.projectunified.craftcommand.annotation.Resolve;
import io.github.projectunified.craftcommand.processor.model.CommandModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;
import javax.lang.model.type.DeclaredType;
import javax.lang.model.type.TypeKind;
import javax.lang.model.type.TypeMirror;
import java.util.List;

/**
 * Compile-time lookups for local resolvers, suggest methods, fields, and
 * command-model class resolution.
 *
 * <p>Holds a {@link ProcessingEnvironment} for type comparison. Constructed
 * once per processor {@link javax.annotation.processing.Processor#init} call.
 */
public final class ResolverLookup {
    private final ProcessingEnvironment env;

    public ResolverLookup(ProcessingEnvironment env) {
        this.env = env;
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
    public ExecutableElement findLocalResolver(CommandModel classModel, io.github.projectunified.craftcommand.processor.model.ParameterModel p) {
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
     */
    public CommandModel findModelForClass(CommandModel current, TypeElement targetClass) {
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
     * Find a no-arg/instance method by name on the given type element.
     * Searches the current class and all parent classes.
     */
    public ExecutableElement findMethod(TypeElement typeElement, String name) {
        TypeElement current = typeElement;
        while (current != null) {
            for (Element enclosed : current.getEnclosedElements()) {
                if (enclosed instanceof ExecutableElement
                        && enclosed.getSimpleName().toString().equals(name)) {
                    return (ExecutableElement) enclosed;
                }
            }
            javax.lang.model.element.Element enclosing = current.getEnclosingElement();
            current = (enclosing instanceof TypeElement) ? (TypeElement) enclosing : null;
        }
        return null;
    }

    /**
     * Find a suggest method by name and validate its signature.
     *
     * <p>Valid signatures (where S is a sender type, T is any type):
     * <ul>
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
    public ExecutableElement findSuggestMethod(TypeElement typeElement, String name) {
        ExecutableElement method = findMethod(typeElement, name);
        if (method == null) {
            env.getMessager().printMessage(javax.tools.Diagnostic.Kind.NOTE, "Suggest method '" + name + "' not found in " + typeElement.getQualifiedName());
            return null;
        }

        if (!isValidSuggestMethod(method)) {
            env.getMessager().printMessage(javax.tools.Diagnostic.Kind.NOTE, "Suggest method '" + name + "' has invalid signature: " + method.getReturnType() + " " + method.getSimpleName() + "(" + method.getParameters() + ")");
            return null;
        }

        return method;
    }

    /**
     * Validates that a method has a valid suggest method signature.
     */
    private boolean isValidSuggestMethod(ExecutableElement method) {
        // Check return type is Collection<String> or subtype
        TypeMirror returnType = method.getReturnType();
        if (!isCollectionOfStrings(returnType)) {
            return false;
        }

        List<? extends javax.lang.model.element.VariableElement> params = method.getParameters();
        int paramCount = params.size();

        // Valid param counts: 1, 2, 3, or 4
        if (paramCount < 1 || paramCount > 4) {
            return false;
        }

        // Check parameter types
        int idx = 0;

        // For 3+ params, check if first param is sender type (not String, not String[])
        if (paramCount >= 3) {
            TypeMirror firstParamType = params.get(0).asType();
            if (!isStringOrStringArray(firstParamType)) {
                idx = 1; // Skip sender param
            } else {
                // First param is String or String[], so no sender
                idx = 0;
            }
        }

        // Current param: must be String[]
        if (idx >= params.size()) return false;
        TypeMirror currentType = params.get(idx).asType();
        if (!isStringArray(currentType)) {
            return false;
        }
        idx++;

        // Optional context param: must be String[]
        if (idx < params.size()) {
            TypeMirror contextType = params.get(idx).asType();
            if (!isStringArray(contextType)) {
                return false;
            }
            idx++;
        }

        return idx == params.size();
    }

    private boolean isCollectionOfStrings(TypeMirror type) {
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

    private boolean isStringArray(TypeMirror type) {
        if (type.getKind() != TypeKind.ARRAY) return false;
        javax.lang.model.type.ArrayType arrayType = (javax.lang.model.type.ArrayType) type;
        TypeMirror componentType = arrayType.getComponentType();
        if (componentType.getKind() != TypeKind.DECLARED) return false;
        TypeElement componentElement = (TypeElement) ((DeclaredType) componentType).asElement();
        return componentElement.getQualifiedName().toString().equals("java.lang.String");
    }

    private boolean isStringOrStringArray(TypeMirror type) {
        if (type.getKind() == TypeKind.DECLARED) {
            TypeElement typeElement = (TypeElement) ((DeclaredType) type).asElement();
            return typeElement.getQualifiedName().toString().equals("java.lang.String");
        }
        return isStringArray(type);
    }

    /**
     * @return true if the type element declares a field with the given name.
     */
    public boolean isField(TypeElement typeElement, String name) {
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed.getKind().isField() && enclosed.getSimpleName().toString().equals(name)) {
                return true;
            }
        }
        return false;
    }
}
