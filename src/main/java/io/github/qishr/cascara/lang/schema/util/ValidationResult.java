package io.github.qishr.cascara.lang.schema.util;

import java.util.ArrayList;
import java.util.List;

public class ValidationResult {
    public enum Severity { ERROR, WARNING }
    private final List<Message> messages = new ArrayList<>();

    // Added line and column
    public record Message(String path, String text, int line, int column, Severity severity) {
        @Override
        public String toString() {
            return String.format("[%d:%d] %s: %s", line, column, path, text);
        }
    }


    public void addError(String path, String text, int line, int column) {
        messages.add(new Message(path, text, line, column, Severity.ERROR));
    }

    public boolean isValid() {
        return messages.stream().noneMatch(m -> m.severity() == Severity.ERROR);
    }

    public List<Message> getMessages() {
        return List.copyOf(messages);
    }
}
