package io.github.projectunified.craftcommand.processor.generator;

import io.github.projectunified.craftcommand.processor.extension.MethodAnnotationHandler;
import io.github.projectunified.craftcommand.processor.extension.ParameterAnnotationHandler;

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
        return ServiceLoader.load(ParameterAnnotationHandler.class, classLoader).stream()
                .<ParameterAnnotationHandler<?>>map(ServiceLoader.Provider::get)
                .toList();
    }

    public static List<MethodAnnotationHandler<?>> loadMethodHandlers(ClassLoader classLoader) {
        return ServiceLoader.load(MethodAnnotationHandler.class, classLoader).stream()
                .<MethodAnnotationHandler<?>>map(ServiceLoader.Provider::get)
                .toList();
    }
}
