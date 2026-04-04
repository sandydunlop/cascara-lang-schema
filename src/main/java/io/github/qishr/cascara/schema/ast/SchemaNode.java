package io.github.qishr.cascara.schema.ast;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.schema.SchemaType;
import io.github.qishr.cascara.schema.rule.ValidationRule;
import io.github.qishr.cascara.schema.util.ValidationResult;

public interface SchemaNode extends AstNode {
    SchemaType getType();

    /// The human-readable title
    String getTitle();

    /// The human-readable description
    String getDescription();

    // Structural Access
    SchemaNode getProperty(String key);
    Map<String, SchemaNode> getProperties();

    // For ARRAY types
    SchemaNode getItemSchema();

    // Definitions & Refs
    boolean isRef();
    String getRef();
    SchemaNode getDefinition(String name);
    Map<String, SchemaNode> getDefinitions();
    URI getOriginUri();
    String getDynamicAnchor();

    // Data Defaults & Validation
    Object getDefaultValue();
    List<ValidationRule> getRules();

    void validate(AstNode node, String path, ValidationResult result);
    AstNode getOriginAst();

    String getContentMediaType();
    void setContentMediaType(String contentMediaType);

    void setExtension(String key, Object value);
    Object getExtension(String key);
    Map<String,Object> getExtensions();

    boolean isReadOnly();
    void setReadOnly(boolean readOnly);


    default String getFormat() {
        return "";
    }

    default String getFormatOption(String key) {
        return "";
    }

    default boolean getBooleanOption(String key, boolean defaultValue) {
        String val = getFormatOption(key);
        if (val == null || val.isEmpty()) return defaultValue;
        return Boolean.parseBoolean(val);
    }

    void addAllOf(SchemaNode node);
    List<SchemaNode> getAllOf();
    SchemaNode getPropertySchema(String key);

    default boolean areAdditionalPropertiesAllowed() {
        return true;
    }

    default SchemaNode getAdditionalPropertiesSchema() {
        return null;
    }


    /// Returns the schema that defines the structure of THIS node.
    /// For a standard property, this returns the JSON Schema Meta-Schema.
    /// For a CEMA property, this might return the CEMA Meta-Schema.
    SchemaNode getMetaSchema();
}