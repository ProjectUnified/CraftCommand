package io.github.projectunified.craftcommand.processor;

import io.github.projectunified.craftcommand.processor.extension.MethodAnnotationHandler;
import io.github.projectunified.craftcommand.processor.extension.ParameterAnnotationHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Loads processor extension handlers (parameter + method annotation handlers)
 * via Java SPI. Processor-side only — never used at runtime.
 *
 * <p>Replaces the inline {@code loadExtensions()} method that previously lived
 * in {@link BaseCommandProcessor}.
 */
public final class SpiLoader {

    private SpiLoader() {
    }

    /**
     * @return all registered {@link ParameterAnnotationHandler} instances,
     * discovered via {@link ServiceLoader} on the given classloader.
     */
    public static List<ParameterAnnotationHandler<?>> loadParameterHandlers(ClassLoader classLoader) {
        List<ParameterAnnotationHandler<?>> list = new ArrayList<>();
        for (ParameterAnnotationHandler<?> h : ServiceLoader.load(ParameterAnnotationHandler.class, classLoader)) {
            list.add(h);
        }
        return list;
    }

    /**
     * @return all registered {@link MethodAnnotationHandler} instances,
     * discovered via {@link ServiceLoader} on the given classloader.
     */
    public static List<MethodAnnotationHandler<?>> loadMethodHandlers(ClassLoader classLoader) {
        List<MethodAnnotationHandler<?>> list = new ArrayList<>();
        for (MethodAnnotationHandler<?> h : ServiceLoader.load(MethodAnnotationHandler.class, classLoader)) {
            list.add(h);
        }
        return list;
    }
}
