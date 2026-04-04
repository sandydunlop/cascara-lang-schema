package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

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

        Set<Object> seen = new HashSet<>();
        for (int i = 0; i < sequence.getChildren().size(); i++) {
            AstNode item = sequence.getChildren().get(i);

            if (item instanceof ScalarAstNode scalar) {
                Object val = scalar.getPrimitiveValue();
                if (!seen.add(val)) {
                    result.addError(
                        path + "[" + i + "]",
                        String.format("Duplicate item found: '%s'. Array must have unique items.", val),
                        item.getStartLine(),
                        item.getStartColumn()
                    );
                }
            }
            // Note: For complex objects, we need a custom equals()
            // implementation for the AST subtree.
        }
    }
}