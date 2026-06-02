package io.github.qishr.cascara.schema.rule;

import java.util.regex.Pattern;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

public class RegexRule implements ValidationRule {
    private final Pattern pattern;
    private final String patternString;

    public RegexRule(String pattern) {
        this.patternString = pattern;
        this.pattern = Pattern.compile(pattern);
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        if (node instanceof ScalarAstNode scalar) {
            validateValue(scalar.getPrimitive(), path, result, node.getStartLine(), node.getStartColumn());
        }
    }

    @Override
    public void validateValue(Object value, String path, ValidationResult result) {
        validateValue(value, path, result, -1, -1);
    }

    private void validateValue(Object value, String path, ValidationResult result, int line, int col) {
        if (value != null) {
            String strValue = String.valueOf(value);
            if (!pattern.matcher(strValue).matches()) {
                String msg = "Value does not match the required pattern: " + patternString;
                result.addError(path, msg, line, col);
            }
        }
    }

    public Pattern getPattern() {
        return pattern;
    }

    public String getPatternString() {
        return patternString;
    }
}