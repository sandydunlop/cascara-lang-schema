package io.github.qishr.cascara.lang.schema.ast;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.lang.schema.util.ValidationResult;

public class ObjectSchemaNode extends BaseSchemaNode {
    private final Map<String, SchemaNode> properties = new LinkedHashMap<>();

    public ObjectSchemaNode(String name) {
        super(name, SchemaType.OBJECT);
    }

    public void addProperty(String name, SchemaNode node) {
        this.properties.put(name, node);
    }

    @Override
    public Map<String, SchemaNode> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public SchemaNode getItemTemplate() { return null; }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        super.validate(node, path, result);

        if (node instanceof MapAstNode mapNode) {
            properties.forEach((key, childSchema) -> {
                // Use the Map interface's 'get' for cleaner lookups
                AstNode dataNode = mapNode.get(key);
                String childPath = path.isEmpty() ? key : path + "." + key;

                if (dataNode != null) {
                    childSchema.validate(dataNode, childPath, result);
                } else {
                    result.addError(childPath, "Missing required property: " + key,
                                    node.getStartLine(), node.getStartColumn());
                }
            });
        }
    }

    @Override
    public SchemaNode getPropertySchema(String key) {
        // BaseSchemaNode now handles the recursive allOf search
        return super.getPropertySchema(key);
    }
}