package io.github.projectunified.craftcommand.processor.standalone;

import com.google.auto.service.AutoService;
import io.github.projectunified.craftcommand.processor.BaseCommandProcessor;
import io.github.projectunified.craftcommand.processor.generator.CommandGeneratorFactory;

import javax.annotation.processing.Processor;
import javax.annotation.processing.SupportedAnnotationTypes;
import javax.annotation.processing.SupportedSourceVersion;
import javax.lang.model.SourceVersion;

/**
 * Annotation processor for standalone command wrapper generation.
 */
@SupportedAnnotationTypes("io.github.projectunified.craftcommand.annotation.Command")
@SupportedSourceVersion(SourceVersion.RELEASE_8)
@AutoService(Processor.class)
public class StandaloneCommandProcessor extends BaseCommandProcessor {
    @Override
    protected CommandGeneratorFactory getGeneratorFactory() {
        return StandaloneCommandGenerator::new;
    }
}
