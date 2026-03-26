package io.github.qishr.cascara.schema.constraint;

public interface SchemaConstraint {
    boolean validate(Object value);
}