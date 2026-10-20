package io.github.projectunified.craftcommand.processor;

import io.github.projectunified.craftcommand.annotation.Command;
import io.github.projectunified.craftcommand.processor.generator.CommandGenerator;
import io.github.projectunified.craftcommand.processor.generator.CommandGeneratorFactory;
import io.github.projectunified.craftcommand.processor.model.CommandModel;
import io.github.projectunified.craftcommand.processor.parser.CommandParserFactory;

import javax.annotation.processing.AbstractProcessor;
import javax.annotation.processing.ProcessingEnvironment;
import javax.annotation.processing.RoundEnvironment;
import javax.lang.model.element.Element;
import javax.lang.model.element.TypeElement;
import javax.tools.Diagnostic;
import java.io.IOException;
import java.util.Set;

/**
 * Dispatches annotated classes to the two halves of the pipeline: the parser builds a {@link CommandModel}, the
 * generator writes the wrapper.
 *
 * <p>A platform processor only implements {@link #getGeneratorFactory()}; all code generation lives in its
 * {@link CommandGenerator}.
 */
public abstract class BaseCommandProcessor extends AbstractProcessor {
    private CommandParserFactory parserFactory;
    private CommandGenerator generator;

    /**
     * The factory creating this platform's generator.
     */
    protected abstract CommandGeneratorFactory getGeneratorFactory();

    @Override
    public synchronized void init(ProcessingEnvironment processingEnv) {
        super.init(processingEnv);
        this.parserFactory = new CommandParserFactory(processingEnv);
        this.generator = getGeneratorFactory().create(processingEnv);
    }

    @Override
    public boolean process(Set<? extends TypeElement> annotations, RoundEnvironment roundEnv) {
        for (Element element : roundEnv.getElementsAnnotatedWith(Command.class)) {
            if (!(element instanceof TypeElement)) continue;
            TypeElement typeElement = (TypeElement) element;
            Element enclosing = typeElement.getEnclosingElement();
            if (enclosing instanceof TypeElement && enclosing.getAnnotation(Command.class) != null) {
                continue; // generated with the enclosing command tree
            }

            CommandModel commandModel = parserFactory.parse(typeElement);
            if (commandModel == null) continue;

            try {
                generator.generate(commandModel);
            } catch (IOException e) {
                if (!e.getClass().getName().contains("FilerException")) {
                    processingEnv.getMessager().printMessage(Diagnostic.Kind.ERROR,
                            "Failed to generate wrapper: " + e.getMessage(), typeElement);
                }
            }
        }
        return true;
    }
}
