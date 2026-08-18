package io.github.projectunified.craftcommand.processor;

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
 * <p>Type registration: override {@link BaseCommandProcessor#registerTypes}.
 */
public final class SpiLoader {

    private SpiLoader() {
    }

    @SuppressWarnings("unchecked")
    public static List<ParameterAnnotationHandler<?>> loadParameterHandlers(ClassLoader classLoader) {
        List<ParameterAnnotationHandler<?>> list = new ArrayList<>();
        for (Object o : ServiceLoader.load(ParameterAnnotationHandler.class, classLoader)) {
            list.add((ParameterAnnotationHandler<?>) o);
        }
        return list;
    }

    @SuppressWarnings("unchecked")
    public static List<MethodAnnotationHandler<?>> loadMethodHandlers(ClassLoader classLoader) {
        List<MethodAnnotationHandler<?>> list = new ArrayList<>();
        for (Object o : ServiceLoader.load(MethodAnnotationHandler.class, classLoader)) {
            list.add((MethodAnnotationHandler<?>) o);
        }
        return list;
    }
}
