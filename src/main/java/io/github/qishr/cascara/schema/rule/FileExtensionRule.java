package io.github.qishr.cascara.schema.rule;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

// TODO: Should this not be elsewhere?
public class FileExtensionRule implements ValidationRule {
    private final String[] extensions;

    public FileExtensionRule(String[] extensions) {
        this.extensions = extensions;
    }

    public String[] getExtensions() {
        return extensions;
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        // Optional: Implement actual validation logic here if desired
    }
}