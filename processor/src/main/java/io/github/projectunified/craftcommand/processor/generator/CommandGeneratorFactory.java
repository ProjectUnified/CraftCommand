package io.github.projectunified.craftcommand.processor.generator;

import javax.annotation.processing.ProcessingEnvironment;

/**
 * Creates the platform generator of one annotation-processing session.
 *
 * <p>A platform processor supplies a factory, usually as a constructor reference:
 * {@code return BukkitCommandGenerator::new;}.
 */
@FunctionalInterface
public interface CommandGeneratorFactory {
    /**
     * Creates the generator bound to the current processing environment.
     *
     * @param processingEnvironment the processing environment of the current compiler round
     * @return the platform generator
     */
    CommandGenerator create(ProcessingEnvironment processingEnvironment);
}
