package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

import java.util.List;
import java.util.Map;

public class RequiredRule implements ValidationRule {
    private final List<String> requiredKeys;

    public RequiredRule(List<String> requiredKeys) {
        this.requiredKeys = requiredKeys;
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        if (node instanceof MapAstNode mapNode) {
            validateValue(mapNode, path, result, node.getStartLine(), node.getStartColumn());
        }
    }

    @Override
    public void validateValue(Object value, String path, ValidationResult result) {
        validateValue(value, path, result, -1, -1);
    }

    private void validateValue(Object value, String path, ValidationResult result, int line, int col) {
        // In the editor, 'value' is expected to be the Map/Object containing the keys
        if (value instanceof Map<?, ?> map) {
            for (String key : requiredKeys) {
                if (!map.containsKey(key)) {
                    String msg = "Missing required property: " + key;
                    result.addError(path, msg, line, col);
                }
            }
        } else if (value instanceof MapAstNode mapNode) {
            // Helper for the bridge
            for (String key : requiredKeys) {
                if (mapNode.get(key) == null) {
                    String msg = "Missing required property: " + key;
                    result.addError(path, msg, line, col);
                }
            }
        }
    }

    public List<String> getRequiredKeys() {
        return requiredKeys;
    }
}