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
            Object val = scalar.getPrimitiveValue();
            if (val instanceof Number num) {
                if (num.doubleValue() < min) {
                    result.addError(path,
                        String.format("Value %s is less than the minimum allowed (%s)", num, min),
                        node.getStartLine(), node.getStartColumn());
                }
            }
        }
    }

    public double getMin() {
        return min;
    }
}