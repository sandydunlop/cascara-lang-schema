package io.github.qishr.cascara.lang.schema.constraint;

public interface SchemaConstraint {
    boolean validate(Object value);
}