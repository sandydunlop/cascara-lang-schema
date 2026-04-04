package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

public interface ValidationRule {
    /**
     * Validates a node and adds errors to the result if necessary.
     */
    void validate(AstNode node, String path, ValidationResult result);

    /**
     * Legacy support or simple checks .
     */
    default boolean isValid(Object value) {
        return true;
    }

    default void validateValue(Object value, String path, ValidationResult result) {
        // Default implementation can be empty or a bridge
    }
}
