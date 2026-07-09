package io.github.projectunified.craftcommand.example.standalone;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertTrue;

public final class TestHelpers {
    private TestHelpers() {
    }

    public static void assertSuggestionsContain(List<String> suggestions, String... expected) {
        for (String s : expected) {
            assertTrue(suggestions.contains(s), "Expected suggestion: " + s + ", got: " + suggestions);
        }
    }
}
