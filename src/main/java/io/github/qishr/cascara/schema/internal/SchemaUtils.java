package io.github.qishr.cascara.schema.internal;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.schema.SchemaException;

public class SchemaUtils {

    public static AstNode resolveFragment(AstNode root, String fragment) throws SchemaException {
        if (fragment == null || fragment.isEmpty() || fragment.equals("#") || fragment.equals("/")) {
            return root;
        }

        // Strip leading '#' if present
        String path = fragment.startsWith("#") ? fragment.substring(1) : fragment;

        // Split by '/', then filter out empty segments (like the leading one in /definitions)
        String[] parts = path.split("/");
        AstNode currentNode = root;

        for (String part : parts) {
            if (part.isEmpty()) continue;

            if (currentNode instanceof MapAstNode map) {
                // Using the key exactly as it appears in the segment (e.g., "$defs")
                currentNode = (AstNode) map.get(part);
            } else {
                return null;
            }

            if (currentNode == null) return null;
        }
        return currentNode;
    }
}
