package io.github.qishr.cascara.schema.constraint;

public record RangeConstraint(Double min, Double max) implements SchemaConstraint {
    @Override
    public boolean validate(Object value) {
        if (!(value instanceof Number n)) return false;
        double d = n.doubleValue();
        return (min == null || d >= min) && (max == null || d <= max);
    }
}