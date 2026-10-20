package io.github.projectunified.craftcommand.processor.generator;

import io.github.projectunified.craftcommand.processor.extension.MethodAnnotationHandler;
import io.github.projectunified.craftcommand.processor.extension.ParameterAnnotationHandler;

import java.util.ArrayList;
import java.util.List;
import java.util.ServiceLoader;

/**
 * Loads processor extension handlers via Java SPI. Processor-side only — never used at runtime.
 *
 * <p>Discovers:
 * <ul>
 *   <li>{@link ParameterAnnotationHandler} — parameter-level validation/extension</li>
 *   <li>{@link MethodAnnotationHandler} — method-level annotation injection</li>
 * </ul>
 *
 * <p>Type registration happens in the generator constructor through {@code typeSupport().register(...)}.
 */
public final class SpiLoader {

    private SpiLoader() {
    }

    public static List<ParameterAnnotationHandler<?>> loadParameterHandlers(ClassLoader classLoader) {
        List<ParameterAnnotationHandler<?>> handlers = new ArrayList<>();
        for (ParameterAnnotationHandler<?> handler : ServiceLoader.load(ParameterAnnotationHandler.class, classLoader)) {
            handlers.add(handler);
        }
        return handlers;
    }

    public static List<MethodAnnotationHandler<?>> loadMethodHandlers(ClassLoader classLoader) {
        List<MethodAnnotationHandler<?>> handlers = new ArrayList<>();
        for (MethodAnnotationHandler<?> handler : ServiceLoader.load(MethodAnnotationHandler.class, classLoader)) {
            handlers.add(handler);
        }
        return handlers;
    }
}
