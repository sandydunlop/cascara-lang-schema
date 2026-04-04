package io.github.qishr.cascara.schema.ast;

import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.Map;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.schema.SchemaType;
import io.github.qishr.cascara.schema.util.ValidationResult;

public class ObjectSchemaNode extends BaseSchemaNode {
    private final Map<String, SchemaNode> properties = new LinkedHashMap<>();

    private boolean additionalPropertiesAllowed = true;
    private SchemaNode additionalPropertiesSchema;

    private boolean unevaluatedPropertiesAllowed = true;
    private SchemaNode unevaluatedPropertiesSchema;

    public ObjectSchemaNode(SchemaNode metaSchema) {
        super(SchemaType.OBJECT, metaSchema);
    }

    public void addProperty(String name, SchemaNode node) {
        this.properties.put(name, node);
    }

    @Override
    public Map<String, SchemaNode> getProperties() {
        return Collections.unmodifiableMap(properties);
    }

    @Override
    public SchemaNode getItemSchema() { return null; }

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

    public void setAdditionalPropertiesSchema(SchemaNode schema) {
        this.additionalPropertiesSchema = schema;
        // If a schema is set, it implies additional properties are allowed
        this.additionalPropertiesAllowed = (schema != null);
    }

    public SchemaNode getAdditionalPropertiesSchema() {
        return additionalPropertiesSchema;
    }

    public void setUnevaluatedPropertiesSchema(SchemaNode schema) {
        this.unevaluatedPropertiesSchema = schema;
        this.unevaluatedPropertiesAllowed = (schema != null);
    }

    public SchemaNode getUnevaluatedPropertiesSchema() {
        return unevaluatedPropertiesSchema;
    }

    public void setUnevaluatedPropertiesAllowed(boolean allowed) { this.unevaluatedPropertiesAllowed = allowed; }
    public boolean areUnevaluatedPropertiesAllowed() { return unevaluatedPropertiesAllowed; }
    public void setAdditionalPropertiesAllowed(boolean b) { additionalPropertiesAllowed = b; }
    @Override
    public boolean areAdditionalPropertiesAllowed() { return additionalPropertiesAllowed; }
}