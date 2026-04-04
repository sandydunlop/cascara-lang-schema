package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

public class MaxItemsRule implements ValidationRule {
    private final int maxItems;

    public MaxItemsRule(int maxItems) {
        this.maxItems = maxItems;
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        // This rule only applies to sequences
        if (node instanceof SequenceAstNode sequence) {
            int currentSize = sequence.getChildren().size();
            if (currentSize > maxItems) {
                result.addError(
                    path,
                    String.format("Array has too many items (%d). Maximum allowed is %d.", currentSize, maxItems),
                    node.getStartLine(),
                    node.getStartColumn()
                );
            }
        }
    }

    public int getMaxItems() {
        return maxItems;
    }

}