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
            Object val = scalar.getPrimitiveValue();
            if (val instanceof Number num) {
                if (num.doubleValue() > max) {
                    result.addError(path,
                        String.format("Value %s is more than the maximum allowed (%s)", num, max),
                        node.getStartLine(), node.getStartColumn());
                }
            }
        }
    }

    public double getMax() {
        return max;
    }
}