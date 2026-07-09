package io.github.projectunified.craftcommand.example.bukkit;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.mockbukkit.mockbukkit.MockBukkit;
import org.mockbukkit.mockbukkit.ServerMock;

public abstract class AbstractBukkitCommandTest {
    protected ServerMock server;

    @BeforeEach
    public void setUp() {
        server = MockBukkit.mock();
        registerCommand();
    }

    @AfterEach
    public void tearDown() {
        MockBukkit.unmock();
    }

    protected abstract void registerCommand();
}
