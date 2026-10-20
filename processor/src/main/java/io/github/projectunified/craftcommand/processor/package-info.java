/**
 * Base annotation processor for CraftCommand.
 *
 * <p>{@link io.github.projectunified.craftcommand.processor.BaseCommandProcessor} is a thin dispatcher: it hands
 * annotated classes to the parser factory and the resulting models to a platform generator factory. The two
 * halves live in {@code parser} and {@code generator}.
 *
 * <p>Annotations are read with the standard {@code Element#getAnnotation(Class)} API, so no prism generation step
 * or extra annotation-processing dependency is needed.
 */
package io.github.projectunified.craftcommand.processor;
