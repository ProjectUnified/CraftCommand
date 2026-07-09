package io.github.projectunified.craftcommand.example.paper;

import io.github.projectunified.craftcommand.CommandManager;

class TestPaperManager extends CommandManager<Object> {
    TestPaperManager() {
        super((sender, exception) -> {
            if (exception instanceof RuntimeException) throw (RuntimeException) exception;
            throw new RuntimeException(exception);
        });
    }

    @Override
    public void register(Object command) {
    }
}
