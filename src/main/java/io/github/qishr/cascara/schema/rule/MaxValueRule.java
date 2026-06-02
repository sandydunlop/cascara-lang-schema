package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

public class MaxValueRule implements ValidationRule {
    private final double max;

    public MaxValueRule(double max) {
        this.max = max;
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
        if (value instanceof Number num) {
            if (num.doubleValue() > max) {
                String msg = String.format("Value %s is more than the maximum allowed (%s)", num, max);
                result.addError(path, msg, line, col);
            }
        }
    }

    public double getMax() {
        return max;
    }
}