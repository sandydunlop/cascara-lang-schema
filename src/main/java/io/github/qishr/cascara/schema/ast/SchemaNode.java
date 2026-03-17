package io.github.qishr.cascara.schema.ast;

import java.net.URI;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.schema.rule.ValidationRule;
import io.github.qishr.cascara.schema.util.ValidationResult;

public interface SchemaNode extends AstNode {
    String getName();
    SchemaType getType();

    /// The human-readable title
    String getTitle();

    /// The human-readable description
    String getDescription();

    // Structural Access
    Optional<SchemaNode> getProperty(String key);
    Map<String, SchemaNode> getProperties();

    // For ARRAY types
    SchemaNode getItemTemplate();

    // Definitions & Refs
    boolean isRef();
    String getRef();
    Optional<SchemaNode> getDefinition(String name);
    Map<String, SchemaNode> getDefinitions();
    URI getUri();

    // Data Defaults & Validation
    Object getDefaultValue();
    List<ValidationRule> getRules();

    void validate(AstNode node, String path, ValidationResult result);
    AstNode getOriginAst();





    // TODO: These will be replaced by custom hints
    // String getOptionProvider();
    // void setOptionProvider(String providerId);
    // String getProviderParameter();
    // void setProviderParameter(String parameter);
    // boolean isHidden();
    // void setHidden(boolean hidden);


    void setCustomHint(String key, Object value);
    Object getCustomHint(String key);
    Map<String,Object> getCustomHints();





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
}