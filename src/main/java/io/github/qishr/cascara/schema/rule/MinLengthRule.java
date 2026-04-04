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
            Object val = scalar.getPrimitiveValue();
            if (val instanceof String str) {
                int length = str.length();
                if (length < min) {
                    result.addError(path,
                        String.format("Length %s is less than the minimum allowed (%s)", length, min),
                        node.getStartLine(), node.getStartColumn());
                }
            }
        }
    }

    public int getMin() {
        return min;
    }
}