package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

import java.util.Collection;
import java.util.HashSet;
import java.util.Set;

public class UniqueItemsRule implements ValidationRule {
    private final boolean active;

    public UniqueItemsRule(boolean active) {
        this.active = active;
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        if (!active || !(node instanceof SequenceAstNode sequence)) return;

        // Bridge to the common logic, but maintaining individual item reporting
        // which requires a loop here to capture specific line/column info per duplicate.
        Set<Object> seen = new HashSet<>();
        int i = 0;
        for (AstNode item : sequence.getChildren()) {
            if (item instanceof ScalarAstNode scalar) {
                Object val = scalar.getPrimitive();
                if (!seen.add(val)) {
                    String msg = String.format("Duplicate item found: '%s'. Array must have unique items.", val);
                    result.addError(path + "[" + i + "]", msg, item.getStartLine(), item.getStartColumn());
                }
            }
            i++;
        }
    }

    @Override
    public void validateValue(Object value, String path, ValidationResult result) {
        validateValue(value, path, result, -1, -1);
    }

    private void validateValue(Object value, String path, ValidationResult result, int line, int col) {
        if (!active || value == null) return;

        if (value instanceof Collection<?> collection) {
            Set<Object> seen = new HashSet<>();
            for (Object item : collection) {
                if (!seen.add(item)) {
                    String msg = String.format("Duplicate item found: '%s'. Array must have unique items.", item);
                    // In value-only mode (editor), we report on the main path as we don't have sub-node coords
                    result.addError(path, msg, line, col);
                    break; // One error is usually enough for live editor feedback
                }
            }
        }
    }

    public boolean isActive() {
        return active;
    }
}