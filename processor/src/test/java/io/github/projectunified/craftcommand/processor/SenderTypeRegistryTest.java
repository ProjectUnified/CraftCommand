package io.github.projectunified.craftcommand.processor;

import com.palantir.javapoet.ClassName;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

public class SenderTypeRegistryTest {
    private SenderTypeRegistry registry;

    @BeforeEach
    public void setUp() {
        registry = new SenderTypeRegistry();
    }

    @Test
    public void testRegisterBaseType() {
        registry.registerSenderBaseType("com.example.BaseSender");
        assertTrue(registry.isSenderType(ClassName.get("com.example", "BaseSender")));
        assertTrue(registry.isSenderBaseType(ClassName.get("com.example", "BaseSender")));
    }

    @Test
    public void testRegisterSenderType() {
        registry.registerSenderType("com.example.Player");
        assertTrue(registry.isSenderType(ClassName.get("com.example", "Player")));
        assertFalse(registry.isSenderBaseType(ClassName.get("com.example", "Player")));
    }

    @Test
    public void testUnregisteredType() {
        assertFalse(registry.isSenderType(ClassName.get("com.example", "Unknown")));
        assertFalse(registry.isSenderBaseType(ClassName.get("com.example", "Unknown")));
    }

    @Test
    public void testBaseTypeAlsoInSenderTypes() {
        registry.registerSenderBaseType("com.example.Base");
        assertTrue(registry.isSenderType(ClassName.get("com.example", "Base")));
        assertTrue(registry.isSenderBaseType(ClassName.get("com.example", "Base")));
    }
}
