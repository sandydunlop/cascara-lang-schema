package io.github.qishr.cascara.schema.util;

import java.util.LinkedHashMap;
import java.util.Map;

import io.github.qishr.cascara.schema.SchemaType;
import io.github.qishr.cascara.schema.ast.ObjectSchemaNode;
import io.github.qishr.cascara.schema.ast.SchemaNode;
import io.github.qishr.cascara.schema.rule.*;

public final class SchemaNodeToPlain {

    public Object toPlain(SchemaNode node) {
        return toPlain(node, true);
    }

    private Object toPlain(SchemaNode node, boolean isRoot) {
        if (node.isRef()) {
            String refValue = node.getRef() != null ? node.getRef() : "#";
            if (refValue.startsWith("#") && !refValue.contains("/")) {
                return Map.of("$dynamicRef", refValue);
            } else {
                return Map.of("$ref", refValue);
            }
        }

        SchemaType type = node.getType();
        if (type == null || type == SchemaType.ANY) {
            return serializeGeneric(node, isRoot);
        }

        return switch (type) {
            case STRING  -> serializeScalar(node, "string");
            case BOOLEAN -> serializeScalar(node, "boolean");
            case INTEGER -> serializeScalar(node, "integer");
            case NUMBER  -> serializeScalar(node, "number");
            case NULL    -> serializeScalar(node, "null");
            case ARRAY   -> serializeArray(node, isRoot);
            case OBJECT  -> serializeObject(node, isRoot);
            default      -> serializeGeneric(node, isRoot);
        };
    }

    private Map<String, Object> serializeScalar(SchemaNode node, String typeName) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", typeName);
        applyMetadataAndRules(node, map);
        return map;
    }

    private Map<String, Object> serializeGeneric(SchemaNode node, boolean isRoot) {
        Map<String, Object> map = new LinkedHashMap<>();
        applyMetadataAndRules(node, map);
        return map;
    }

    private Map<String, Object> serializeObject(SchemaNode node, boolean isRoot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "object");

        // properties
        if (!node.getProperties().isEmpty()) {
            Map<String, Object> props = new LinkedHashMap<>();
            for (var entry : node.getProperties().entrySet()) {
                props.put(entry.getKey(), toPlain(entry.getValue(), false));
            }
            map.put("properties", props);
        }

        // NEW: handle additionalProperties as Schema
        if (node instanceof ObjectSchemaNode obj) {
            if (obj.getAdditionalPropertiesSchema() != null) {
                map.put("additionalProperties", toPlain(obj.getAdditionalPropertiesSchema(), false));
            } else if (!obj.areAdditionalPropertiesAllowed()) {
                map.put("additionalProperties", false);
            }
        }

        applyMetadataAndRules(node, map);

        if (isRoot && !node.getDefinitions().isEmpty()) {
            Map<String, Object> defs = new LinkedHashMap<>();
            for (var entry : node.getDefinitions().entrySet()) {
                defs.put(entry.getKey(), toPlain(entry.getValue(), false));
            }
            map.put("$defs", defs);
        }

        return map;
    }

    private Map<String, Object> serializeArray(SchemaNode node, boolean isRoot) {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("type", "array");

        SchemaNode item = node.getItemSchema();
        if (item != null) {
            map.put("items", toPlain(item, false));
        }

        applyMetadataAndRules(node, map);
        return map;
    }

    private void applyMetadataAndRules(SchemaNode node, Map<String, Object> target) {
        if (node.getDynamicAnchor() != null) target.put("$dynamicAnchor", node.getDynamicAnchor());
        if (node.getTitle() != null) target.put("title", node.getTitle());
        if (node.getDescription() != null) target.put("description", node.getDescription());
        if (node.getDefaultValue() != null) target.put("default", node.getDefaultValue());

        applyRulesToMap(node, target);
        target.putAll(node.getExtensions());
    }

    private void applyRulesToMap(SchemaNode node, Map<String, Object> target) {
        for (ValidationRule rule : node.getRules()) {
            if (rule instanceof EnumRule er) {
                target.put("enum", er.getAllowedValues());
            } else if (rule instanceof MinValueRule min) {
                target.put("minimum", min.getMin());
            } else if (rule instanceof MaxValueRule max) {
                target.put("maximum", max.getMax());
            } else if (rule instanceof RequiredRule req) {
                target.put("required", req.getRequiredKeys());
            } else if (rule instanceof MinItemsRule minI) {
                target.put("minItems", minI.getMinItems());
            } else if (rule instanceof MaxItemsRule maxI) {
                target.put("maxItems", maxI.getMaxItems());
            }
        }
    }
}