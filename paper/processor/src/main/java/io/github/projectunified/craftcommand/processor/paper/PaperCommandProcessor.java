package io.github.projectunified.craftcommand.processor.paper;

import com.google.auto.service.AutoService;
import io.github.projectunified.craftcommand.processor.BaseCommandProcessor;
import io.github.projectunified.craftcommand.processor.generator.CommandGeneratorFactory;

import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;

/**
 * Annotation processor for Paper command wrapper generation.
 */
@SupportedAnnotationTypes("io.github.projectunified.craftcommand.annotation.Command")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class PaperCommandProcessor extends BaseCommandProcessor {
    @Override
    protected CommandGeneratorFactory getGeneratorFactory() {
        return PaperCommandGenerator::new;
    }
}
