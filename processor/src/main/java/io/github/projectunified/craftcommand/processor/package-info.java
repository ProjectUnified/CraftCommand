/**
 * Base annotation processor for CraftCommand.
 *
 * <p>Contains {@link io.github.projectunified.craftcommand.processor.BaseCommandProcessor}
 * which generates platform-specific command wrappers from {@code @Command} annotations.
 * Platform processors extend this class to customize output per server type.
 *
 * <p>Prisms generated in this package give all platform processors typed, mirror-based access to
 * the CraftCommand annotations without reflective {@code Element#getAnnotation(Class)} calls.
 */
@GeneratePrism(value = Command.class, publicAccess = true)
@GeneratePrism(value = Default.class, publicAccess = true)
@GeneratePrism(value = Resolve.class, publicAccess = true)
@GeneratePrism(value = Name.class, publicAccess = true)
@GeneratePrism(value = Greedy.class, publicAccess = true)
@GeneratePrism(value = Suggest.class, publicAccess = true)
package io.github.projectunified.craftcommand.processor;

import io.avaje.prism.GeneratePrism;
import io.github.projectunified.craftcommand.annotation.*;
