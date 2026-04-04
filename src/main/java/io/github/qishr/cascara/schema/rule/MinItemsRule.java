package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

public class MinItemsRule implements ValidationRule {
    private final int minItems;

    public MinItemsRule(int minItems) {
        this.minItems = minItems;
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        // Only valid if the node is a sequence
        if (node instanceof SequenceAstNode sequence) {
            int currentSize = sequence.getChildren().size();
            if (currentSize < minItems) {
                result.addError(
                    path,
                    String.format("Array has too few items (%d). Minimum required is %d.", currentSize, minItems),
                    node.getStartLine(),
                    node.getStartColumn()
                );
            }
        } else if (minItems > 0) {
            // If the node is missing or not a sequence but minItems > 0, it's an error
            result.addError(path, "Expected an array with at least " + minItems + " items.",
                           node.getStartLine(), node.getStartColumn());
        }
    }

    public int getMinItems() {
        return minItems;
    }
}