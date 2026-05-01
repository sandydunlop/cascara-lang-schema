package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

import java.util.Collection;

public class MaxItemsRule implements ValidationRule {
    private final int maxItems;

    public MaxItemsRule(int maxItems) {
        this.maxItems = maxItems;
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        if (node instanceof SequenceAstNode sequence) {
            validateValue(sequence.getChildren(), path, result, node.getStartLine(), node.getStartColumn());
        }
    }

    @Override
    public void validateValue(Object value, String path, ValidationResult result) {
        validateValue(value, path, result, -1, -1);
    }

    private void validateValue(Object value, String path, ValidationResult result, int line, int col) {
        int currentSize = -1;

        if (value instanceof Collection<?> collection) {
            currentSize = collection.size();
        } else if (value instanceof Iterable<?> iterable) {
            // Fallback for custom iterables if necessary
            int count = 0;
            for (Object ignored : iterable) count++;
            currentSize = count;
        }

        if (currentSize > maxItems) {
            String msg = String.format("Array has too many items (%d). Maximum allowed is %d.", currentSize, maxItems);
            result.addError(path, msg, line, col);
        }
    }

    public int getMaxItems() {
        return maxItems;
    }
}