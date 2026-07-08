package io.github.projectunified.craftcommand.processor;

import io.github.projectunified.craftcommand.processor.extension.CommandValidator;
import io.github.projectunified.craftcommand.processor.extension.MethodAnnotationHandler;
import io.github.projectunified.craftcommand.processor.extension.ParameterAnnotationHandler;
import io.github.projectunified.craftcommand.processor.extension.SuggestionProvider;

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
 *   <li>{@link SuggestionProvider} — global suggestion providers</li>
 *   <li>{@link CommandValidator} — method-level validation/wrapping</li>
 * </ul>
 *
 * <p>Type registration: override {@link BaseCommandProcessor#registerTypes}.
 * Execution source: override {@link BaseCommandProcessor#createExecutionSource}.
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

    public static List<SuggestionProvider> loadSuggestionProviders(ClassLoader classLoader) {
        return loadAll(SuggestionProvider.class, classLoader);
    }

    @SuppressWarnings("unchecked")
    public static List<CommandValidator<?>> loadCommandValidators(ClassLoader classLoader) {
        List<CommandValidator<?>> list = new ArrayList<>();
        for (Object o : ServiceLoader.load(CommandValidator.class, classLoader)) {
            list.add((CommandValidator<?>) o);
        }
        return list;
    }

    private static <T> List<T> loadAll(Class<T> type, ClassLoader classLoader) {
        List<T> list = new ArrayList<>();
        for (T t : ServiceLoader.load(type, classLoader)) {
            list.add(t);
        }
        return list;
    }
}
