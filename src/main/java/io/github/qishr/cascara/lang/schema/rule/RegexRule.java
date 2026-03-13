package io.github.qishr.cascara.lang.schema.rule;

import java.util.regex.Pattern;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.lang.schema.util.ValidationResult;

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
            String value = String.valueOf(scalar.getPrimitiveValue());
            if (!pattern.matcher(value).matches()) {
                result.addError(path,
                    "Value does not match the required pattern: " + patternString,
                    node.getStartLine(), node.getStartColumn());
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