package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

public class MinLengthRule implements ValidationRule {
    private final int min;

    public MinLengthRule(int min) {
        this.min = min;
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        if (node instanceof ScalarAstNode scalar) {
            validateValue(scalar.getPrimitive(), path, result, node.getStartLine(), node.getStartColumn());
        }
    }

    @Override
    public void validateValue(Object value, String path, ValidationResult result) {
        validateValue(value, path, result, -1, -1);
    }

    private void validateValue(Object value, String path, ValidationResult result, int line, int col) {
        if (value instanceof String str) {
            int length = str.length();
            if (length < min) {
                String msg = String.format("Length %s is less than the minimum allowed (%s)", length, min);
                result.addError(path, msg, line, col);
            }
        }
    }

    public int getMin() {
        return min;
    }
}