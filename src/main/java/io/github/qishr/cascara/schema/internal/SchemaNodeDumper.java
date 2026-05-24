package io.github.qishr.cascara.schema.internal;

import io.github.qishr.cascara.schema.SchemaType;
import io.github.qishr.cascara.schema.structure.SchemaNode;

public final class SchemaNodeDumper {

    public static String dump(SchemaNode node) {
        StringBuilder sb = new StringBuilder();
        dump(node, sb, 0);
        return sb.toString();
    }

    private static void dump(SchemaNode node, StringBuilder sb, int indent) {
        if (node == null) {
            indent(sb, indent).append("null\n");
            return;
        }

        indent(sb, indent).append(node.getClass().getSimpleName()).append(" {\n");

        // Type
        indent(sb, indent + 1).append("type: ").append(node.getType()).append("\n");

        // Object properties
        if (node.getType() == SchemaType.OBJECT) {
            indent(sb, indent + 1).append("properties:\n");
            for (var e : node.getProperties().entrySet()) {
                indent(sb, indent + 2).append(e.getKey()).append(":\n");
                dump(e.getValue(), sb, indent + 3);
            }
        }

        // Array items
        if (node.getType() == SchemaType.ARRAY) {
            indent(sb, indent + 1).append("items:\n");
            dump(node.getItemSchema(), sb, indent + 2);
        }

        // Rules
        if (!node.getRules().isEmpty()) {
            indent(sb, indent + 1).append("rules:\n");
            for (var r : node.getRules()) {
                indent(sb, indent + 2).append(r.getClass().getSimpleName()).append("\n");
            }
        }

        indent(sb, indent).append("}\n");
    }

    private static StringBuilder indent(StringBuilder sb, int indent) {
        return sb.append("  ".repeat(indent));
    }
}
