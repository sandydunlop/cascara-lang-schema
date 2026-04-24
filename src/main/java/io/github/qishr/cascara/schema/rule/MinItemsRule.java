package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

import java.util.Collection;

public class MinItemsRule implements ValidationRule {
    private final int minItems;

    public MinItemsRule(int minItems) {
        this.minItems = minItems;
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        if (node instanceof SequenceAstNode sequence) {
            validateValue(sequence.getChildren(), path, result, node.getStartLine(), node.getStartColumn());
        } else if (minItems > 0) {
            result.addError(path, "Expected an array with at least " + minItems + " items.",
                           node.getStartLine(), node.getStartColumn());
        }
    }

    @Override
    public void validateValue(Object value, String path, ValidationResult result) {
        validateValue(value, path, result, -1, -1);
    }

    private void validateValue(Object value, String path, ValidationResult result, int line, int col) {
        int currentSize = 0;

        if (value instanceof Collection<?> collection) {
            currentSize = collection.size();
        } else if (value instanceof Iterable<?> iterable) {
            for (Object ignored : iterable) currentSize++;
        } else if (value != null) {
            // Not a collection/iterable but exists; depends if you want to treat non-arrays as error here
            return;
        }

        if (currentSize < minItems) {
            String msg = String.format("Array has too few items (%d). Minimum required is %d.", currentSize, minItems);
            result.addError(path, msg, line, col);
        }
    }

    public int getMinItems() {
        return minItems;
    }
}