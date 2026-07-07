/**
 * Base annotation processor for CraftCommand.
 *
 * <p>Contains {@link io.github.projectunified.craftcommand.processor.BaseCommandProcessor}
 * which generates platform-specific command wrappers from {@code @Command} annotations.
 * Platform processors extend this class to customize output per server type.
 */
package io.github.projectunified.craftcommand.processor;
