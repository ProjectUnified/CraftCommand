package io.github.projectunified.craftcommand.example.standalone;

import java.util.ArrayList;
import java.util.List;

public class TestSender {
    private final List<String> messages = new ArrayList<>();
    private final String name;

    public TestSender(String name) {
        this.name = name;
    }

    public void sendMessage(String msg) {
        messages.add(msg);
    }

    public List<String> getMessages() {
        return messages;
    }

    public String getName() {
        return name;
    }

    @Override
    public String toString() {
        return name;
    }
}
