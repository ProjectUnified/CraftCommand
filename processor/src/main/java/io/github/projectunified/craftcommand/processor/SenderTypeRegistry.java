package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.TypeName;

import java.util.HashSet;
import java.util.Set;

/**
 * Registry for sender types supported by a platform.
 *
 * <p>Replaces the scattered {@code isSenderType()} and {@code isSenderBaseType()} overrides
 * across platform processors. Each platform registers its supported sender types once,
 * and the base processor delegates to this registry.
 */
public class SenderTypeRegistry {
    private final Set<String> senderTypes = new HashSet<>();
    private final Set<String> senderBaseTypes = new HashSet<>();

    /**
     * Register a sender type (e.g., Player, CommandSender, CommandSourceStack).
     */
    public void registerSenderType(String typeName) {
        senderTypes.add(typeName);
    }

    /**
     * Register a sender base type (the platform's raw sender type).
     */
    public void registerSenderBaseType(String typeName) {
        senderBaseTypes.add(typeName);
        senderTypes.add(typeName);
    }

    /**
     * @return true if the type is a valid sender type for this platform
     */
    public boolean isSenderType(TypeName type) {
        return senderTypes.contains(type.toString());
    }

    /**
     * @return true if the type is the platform's base sender type
     */
    public boolean isSenderBaseType(TypeName type) {
        return senderBaseTypes.contains(type.toString());
    }
}
