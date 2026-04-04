package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

import java.util.List;

public class RequiredRule implements ValidationRule {
    private final List<String> requiredKeys;

    public RequiredRule(List<String> requiredKeys) {
        this.requiredKeys = requiredKeys;
    }



    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        if (!(node instanceof MapAstNode mapNode)) return;

        for (String key : requiredKeys) {
            if (mapNode.get(key) == null) {
                // Since the key is missing, we report the error on the parent object
                result.addError(path, "Missing required property: " + key,
                               node.getStartLine(), node.getStartColumn());
            }
        }
    }

    @Override
    public boolean isValid(Object value) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'isValid'");
    }



    public List<String> getRequiredKeys() {
        return requiredKeys;
    }
}