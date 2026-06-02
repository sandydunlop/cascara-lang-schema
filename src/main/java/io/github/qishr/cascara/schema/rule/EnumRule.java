package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

import java.util.Collections;
import java.util.List;

public class EnumRule implements ValidationRule {
    private final List<String> allowedValues;

    public EnumRule(List<String> allowedValues) {
        this.allowedValues = allowedValues;
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        if (node instanceof ScalarAstNode scalar) {
            validateValue(scalar.getPrimitive(), path, result);
        }
    }

    @Override
    public void validateValue(Object value, String path, ValidationResult result) {
        if (value == null) return;
        String valStr = value.toString();
        if (!allowedValues.contains(valStr)) {
            result.addError(path, "Value '" + valStr + "' is not in allowed list: " + allowedValues, -1, -1);
        }
    }

    public List<String> getAllowedValues() {
        return Collections.unmodifiableList(allowedValues);
    }
}