package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

public class MaxLengthRule implements ValidationRule {
    private final int max;

    public MaxLengthRule(int max) {
        this.max = max;
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        if (node instanceof ScalarAstNode scalar) {
            validateValue(scalar.getPrimitiveValue(), path, result, node.getStartLine(), node.getStartColumn());
        }
    }

    @Override
    public void validateValue(Object value, String path, ValidationResult result) {
        validateValue(value, path, result, -1, -1);
    }

    private void validateValue(Object value, String path, ValidationResult result, int line, int col) {
        if (value instanceof String str) {
            int length = str.length();
            if (length > max) {
                String msg = String.format("Length %s is more than the maximum allowed (%s)", length, max);
                result.addError(path, msg, line, col);
            }
        }
    }

    public int getMax() {
        return max;
    }
}