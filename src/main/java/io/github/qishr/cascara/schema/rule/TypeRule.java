package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.schema.SchemaType;
import io.github.qishr.cascara.schema.util.ValidationResult;

public class TypeRule implements ValidationRule {
    private final SchemaType expectedType;

    public TypeRule(SchemaType expectedType) {
        this.expectedType = expectedType;
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        if (node instanceof ScalarAstNode scalar) {
            Object value = scalar.getPrimitiveValue();
            if (value == null) return; // Let RequiredRule handle "Missing" vs "Wrong Type"
            boolean valid = switch (expectedType) {
                case STRING -> value instanceof String;
                case INTEGER -> value instanceof Integer || value instanceof Long;
                case NUMBER -> value instanceof Number;
                case BOOLEAN -> value instanceof Boolean;
                default -> true;
            };

            if (!valid) {
                result.addError(path, "Expected type " + expectedType + " but found " +
                               value.getClass().getSimpleName(), node.getStartLine(), node.getStartColumn());
            }
        }
    }

    public void validateValue(Object value, String path, ValidationResult result, int line, int col) {
        if (value == null) return;

        boolean valid = switch (expectedType) {
            case STRING -> value instanceof String;
            case INTEGER -> isInteger(value);
            case NUMBER -> value instanceof Number || isNumeric(value);
            case BOOLEAN -> value instanceof Boolean || isBooleanString(value);
            default -> true;
        };

        if (!valid) {
            result.addError(path, "Expected " + expectedType + " but found " +
                           value.getClass().getSimpleName(), line, col);
        }
    }

    // Helper methods to handle String inputs from the UI TextFields
    private boolean isInteger(Object v) {
        if (v instanceof Integer || v instanceof Long) return true;
        try { Long.parseLong(v.toString()); return true; } catch (Exception e) { return false; }
    }

    private boolean isNumeric(Object v) {
        try { Double.parseDouble(v.toString()); return true; } catch (Exception e) { return false; }
    }

    private boolean isBooleanString(Object v) {
        String s = v.toString().toLowerCase();
        return s.equals("true") || s.equals("false");
    }
}