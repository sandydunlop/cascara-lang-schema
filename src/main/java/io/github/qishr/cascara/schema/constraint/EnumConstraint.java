package io.github.qishr.cascara.schema.constraint;

import java.util.List;

public record EnumConstraint(List<String> options) implements SchemaConstraint {
    @Override
    public boolean validate(Object value) {
        return options.contains(String.valueOf(value));
    }
}