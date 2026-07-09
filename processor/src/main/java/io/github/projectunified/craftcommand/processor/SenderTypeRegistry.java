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
     * Register the platform's base sender type (e.g. CommandSourceStack, CommandSender).
     * This is the type that the platform natively provides.
     */
    public void registerSenderBaseType(String typeName) {
        senderBaseTypes.add(typeName);
        senderTypes.add(typeName);
    }

    /**
     * Register a sender type that can be obtained from the base type
     * (e.g. Player, CommandSender — extracted via getSender() and cast).
     */
    public void registerSenderType(String typeName) {
        senderTypes.add(typeName);
    }

    /**
     * @return true if the type is the platform's base sender type
     */
    public boolean isSenderBaseType(TypeName type) {
        return senderBaseTypes.contains(type.toString());
    }

    /**
     * @return true if the type is any recognized sender type (base or obtainable from it)
     */
    public boolean isSenderType(TypeName type) {
        return senderTypes.contains(type.toString());
    }
}
