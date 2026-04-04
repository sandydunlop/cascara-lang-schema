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
            Object val = scalar.getPrimitiveValue();
            if (val instanceof String str) {
                int length = str.length();
                if (length > max) {
                    result.addError(path,
                        String.format("Length %s is more than the maximum allowed (%s)", length, max),
                        node.getStartLine(), node.getStartColumn());
                }
            }
        }
    }

    public int getMax() {
        return max;
    }
}