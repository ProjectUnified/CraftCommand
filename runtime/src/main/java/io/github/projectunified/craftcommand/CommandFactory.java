package io.github.projectunified.craftcommand;

import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.reflect.Method;

/**
 * Instantiates generated command wrappers via a cached {@link MethodHandle} pointing
 * at the wrapper's static {@code factory(Object, CommandManager)} method.
 *
 * <p>Each generated wrapper class exposes:
 * <pre>{@code
 * public static <PlatformCommand> factory(Object instance, CommandManager<S> manager) {
 *     return new X_Wrapper((X) instance, manager);
 * }
 * }</pre>
 *
 * <p>Platform managers cache one {@code CommandFactory} per command class, so
 * registration does zero {@code Class.forName} / {@code Constructor.newInstance}
 * reflection after the first lookup — only a direct {@link MethodHandle#invoke}.
 */
public final class CommandFactory<S> {
    private final MethodHandle handle;

    /**
     * @param wrapperClass the generated wrapper class (e.g. {@code FooCommand_Standalone}).
     * @throws ReflectiveOperationException if the wrapper lacks a matching {@code factory} method.
     */
    public CommandFactory(Class<?> wrapperClass) throws ReflectiveOperationException {
        Method m = wrapperClass.getDeclaredMethod("factory", Object.class, CommandManager.class);
        this.handle = MethodHandles.lookup().unreflect(m);
    }

    /**
     * @param instance the annotated command instance
     * @param manager  the platform command manager
     * @return the instantiated wrapper, cast to the platform command interface
     */
    @SuppressWarnings("unchecked")
    public Object create(Object instance, CommandManager<S> manager) {
        try {
            return handle.invoke(instance, manager);
        } catch (RuntimeException | Error e) {
            throw e;
        } catch (Throwable t) {
            throw new RuntimeException("Failed to instantiate command wrapper", t);
        }
    }
}
