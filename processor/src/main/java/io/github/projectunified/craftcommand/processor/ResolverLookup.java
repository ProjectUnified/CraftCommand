package io.github.projectunified.craftcommand.processor;

import io.github.projectunified.craftcommand.annotation.Resolve;
import io.github.projectunified.craftcommand.processor.model.CommandModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.ExecutableElement;
import javax.lang.model.element.TypeElement;

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
     * method is matched by name. Otherwise, the method is matched by return
     * type equality. The search walks up the enclosing class hierarchy so
     * resolvers can live in parent command classes.
     *
     * @param classModel the command class where resolution starts
     * @param p          the parameter to resolve
     * @return the matching resolver method, or {@code null} if none
     */
    public ExecutableElement findLocalResolver(CommandModel classModel, io.github.projectunified.craftcommand.processor.model.ParameterModel p) {
        Resolve resolveAnn = p.getElement().getAnnotation(Resolve.class);
        String explicitName = (resolveAnn != null) ? resolveAnn.value() : "";

        TypeElement current = classModel.getElement();
        while (current != null) {
            for (Element enclosed : current.getEnclosedElements()) {
                if (!(enclosed instanceof ExecutableElement)) continue;
                ExecutableElement method = (ExecutableElement) enclosed;
                if (!explicitName.isEmpty()) {
                    if (method.getSimpleName().toString().equals(explicitName)) {
                        return method;
                    }
                } else {
                    Resolve methodResolve = method.getAnnotation(Resolve.class);
                    if (methodResolve != null
                            && env.getTypeUtils().isSameType(method.getReturnType(), p.getType())) {
                        return method;
                    }
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
     */
    public ExecutableElement findMethod(TypeElement typeElement, String name) {
        for (Element enclosed : typeElement.getEnclosedElements()) {
            if (enclosed instanceof ExecutableElement
                    && enclosed.getSimpleName().toString().equals(name)) {
                return (ExecutableElement) enclosed;
            }
        }
        return null;
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
