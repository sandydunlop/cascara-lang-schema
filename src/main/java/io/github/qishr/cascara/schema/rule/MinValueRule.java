package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

public class MinValueRule implements ValidationRule {
    private final double min;

    public MinValueRule(double min) {
        this.min = min;
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
        if (value instanceof Number num) {
            if (num.doubleValue() < min) {
                String msg = String.format("Value %s is less than the minimum allowed (%s)", num, min);
                result.addError(path, msg, line, col);
            }
        }
    }

    public double getMin() {
        return min;
    }
}