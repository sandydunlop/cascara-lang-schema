package io.github.qishr.cascara.lang.schema.util;

import java.util.HashSet;
import java.util.List;
import java.util.Set;

import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.lang.schema.ast.SchemaNode;
import io.github.qishr.cascara.lang.schema.ast.SchemaType;
import io.github.qishr.cascara.lang.schema.rule.RequiredRule;

public class SchemaUtils {

    public static AstNode resolveFragment(AstNode root, String fragment) throws SchemaException {
        if (fragment == null || fragment.isEmpty() || fragment.equals("#") || fragment.equals("/")) {
            return root;
        }

        // Strip leading '#' if present
        String path = fragment.startsWith("#") ? fragment.substring(1) : fragment;

        // Split by '/', then filter out empty segments (like the leading one in /definitions)
        String[] parts = path.split("/");
        String currentName = "";
        AstNode currentNode = root;
        if (currentNode instanceof StructuredDocument doc) {
            currentNode = doc.getRoot();
        }

        for (String part : parts) {
            if (part.isEmpty()) continue;

            if (currentNode instanceof MapAstNode map) {
                // Using the key exactly as it appears in the segment (e.g., "$defs")
                currentNode = (AstNode) map.get(part);
                currentName = part;
            } else {
                return null;
            }

            if (currentNode == null) return null;
        }
        if (currentNode instanceof SimpleMapNode map) {
            map.put("name", new SimpleScalarNode(currentName));
        }
        return currentNode;
    }

    public static String printSchema(SchemaNode root) {
        StringBuilder sb = new StringBuilder();
        generateText(root, sb, 0, new HashSet<>());
        return sb.toString();
    }

    private static void generateText(SchemaNode node, StringBuilder sb, int indent, Set<SchemaNode> visited) {
        if (node == null || !visited.add(node)) return;

        String indentation = "  ".repeat(indent);

        // 1. Basic Node Info
        sb.append(indentation).append(node.getName())
          .append(" (").append(node.getType()).append(")");

        // 2. Default Value
        if (node.getDefaultValue() != null) {
            sb.append(" [default: ").append(node.getDefaultValue()).append("]");
        }
        sb.append("\n");

        // 3. Description (if any)
        if (node.getDescription() != null && !node.getDescription().isEmpty()) {
            sb.append(indentation).append("  # ").append(node.getDescription()).append("\n");
        }

        // 4. Extract "Required" status from the Parent's rules
        List<String> requiredInThisObject = extractRequiredKeys(node);

        // 5. Recurse into Properties (OBJECT)
        if (node.getType() == SchemaType.OBJECT) {
            node.getProperties().forEach((key, child) -> {
                boolean isRequired = requiredInThisObject.contains(key);
                if (isRequired) {
                    sb.append(indentation).append("  *REQUIRED*\n");
                }
                generateText(child, sb, indent + 1, visited);
            });
        }

        // 6. Recurse into Template (ARRAY)
        else if (node.getType() == SchemaType.ARRAY && node.getItemTemplate() != null) {
            sb.append(indentation).append("  Items:\n");
            generateText(node.getItemTemplate(), sb, indent + 1, visited);
        }
    }

    private static List<String> extractRequiredKeys(SchemaNode node) {
        return node.getRules().stream()
            .filter(rule -> rule instanceof RequiredRule)
            .map(rule -> (RequiredRule) rule)
            // Note: You might need a getter in RequiredRule to access requiredKeys
            .flatMap(rule -> rule.getRequiredKeys().stream())
            .toList();
    }
}
