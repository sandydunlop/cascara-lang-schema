package io.github.qishr.cascara.lang.schema.util;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.qishr.cascara.lang.schema.ast.SchemaNode;

public final class SchemaNodeToPlain {

    public Object toPlain(SchemaNode node) {
        return toPlain(node, true);
    }

    private Object toPlain(SchemaNode node, boolean isRoot) {
        // REF nodes: emit $ref only, never descend
        if (node.isRef()) {
            String ref = node.getRef();
            return Map.of("$ref", ref != null ? ref : "#");
        }

        return switch (node.getType()) {
            case STRING -> Map.of("type", "string");
            case BOOLEAN -> Map.of("type", "boolean");
            case INTEGER -> Map.of("type", "integer");
            case NUMBER -> Map.of("type", "number");
            case NULL -> Map.of("type", "integer");
            case ARRAY -> serializeArray(node, isRoot);
            case OBJECT -> serializeObject(node, isRoot);
        };
    }

    private Map<String, Object> serializeObject(SchemaNode node, boolean isRoot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "object");

        // properties
        Map<String, Object> props = new LinkedHashMap<>();
        for (var entry : node.getProperties().entrySet()) {
            props.put(entry.getKey(), toPlain(entry.getValue(), false));
        }
        map.put("properties", props);

        // metadata
        if (node.getTitle() != null) map.put("title", node.getTitle());
        if (node.getDescription() != null) map.put("description", node.getDescription());

        // definitions only at root
        if (isRoot && !node.getDefinitions().isEmpty()) {
            Map<String, Object> defs = new LinkedHashMap<>();
            for (var entry : node.getDefinitions().entrySet()) {
                defs.put(entry.getKey(), toPlain(entry.getValue(), false));
            }
            map.put("definitions", defs);
        }

        return map;
    }

    private Map<String, Object> serializeArray(SchemaNode node, boolean isRoot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "array");

        SchemaNode item = node.getItemTemplate();
        if (item != null) {
            map.put("items", toPlain(item, false));
        }

        return map;
    }
}
