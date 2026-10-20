/**
 * Annotation-reading half of the annotation processor.
 *
 * <p>{@link io.github.projectunified.craftcommand.processor.parser.CommandParserFactory} reads the command
 * annotations into immutable models ({@code CommandModel}, {@code MethodModel}, {@code ParameterModel}).
 * Resolver and suggestion methods are linked here, so code generation never reads the core command annotations.
 * Generators still use elements for types and names, plus SPI handler dispatch and platform annotations.
 */
package io.github.projectunified.craftcommand.processor.parser;
