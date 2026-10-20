package io.github.projectunified.craftcommand.processor.parser;

import io.github.projectunified.craftcommand.processor.model.CommandModel;

import javax.annotation.processing.ProcessingEnvironment;
import javax.lang.model.element.TypeElement;

/**
 * Creates the parser used by a processor round.
 *
 * <p>This is the annotation-reading half of the pipeline. It never emits code, so it can be used and tested
 * independently of the generator.
 */
public final class CommandParserFactory {
    private final CommandParser parser;

    public CommandParserFactory(ProcessingEnvironment processingEnvironment) {
        this.parser = new CommandParser(processingEnvironment.getMessager());
    }

    /**
     * Parses a class annotated with {@code @Command}.
     *
     * @param typeElement the command class
     * @return the command model, or {@code null} when the class cannot be parsed
     */
    public CommandModel parse(TypeElement typeElement) {
        return parser.parse(typeElement);
    }
}
