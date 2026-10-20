/**
 * Code generation half of the annotation processor.
 *
 * <p>{@link io.github.projectunified.craftcommand.processor.generator.CommandGenerator} turns a parsed
 * {@code CommandModel} into a wrapper source file. Generators do not read the core command annotations; the only
 * annotation reads left are SPI handler dispatch (third-party annotations) and platform annotations such as
 * {@code @Permission}.
 *
 * <p>A platform generator extends {@link io.github.projectunified.craftcommand.processor.generator.BaseCommandGenerator}
 * and overrides only the platform hooks; the shared routing, parameter resolution and suggestion code is
 * inherited. See {@code docs/processor-flow.md} for a minimal example.
 */
package io.github.projectunified.craftcommand.processor.generator;
