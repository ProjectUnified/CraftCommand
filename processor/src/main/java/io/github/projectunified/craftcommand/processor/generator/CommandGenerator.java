package io.github.projectunified.craftcommand.processor.generator;

import io.github.projectunified.craftcommand.processor.model.CommandModel;

import java.io.IOException;

/**
 * Turns a parsed {@link CommandModel} into the platform wrapper source file.
 *
 * <p>Implementations read models, not the core command annotations, so parsing and generation can be developed and
 * tested separately. The only annotation reads left are SPI handler dispatch and platform annotations.
 */
public interface CommandGenerator {
    /**
     * Writes the wrapper class of the given command model.
     *
     * @param model the parsed command tree
     * @throws IOException when the source file cannot be written
     */
    void generate(CommandModel model) throws IOException;
}
