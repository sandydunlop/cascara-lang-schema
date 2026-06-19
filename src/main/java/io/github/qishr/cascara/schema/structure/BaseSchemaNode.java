package io.github.qishr.cascara.schema.structure;

import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.schema.SchemaType;
import io.github.qishr.cascara.schema.rule.ValidationRule;
import io.github.qishr.cascara.schema.util.ValidationResult;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public abstract class BaseSchemaNode implements SchemaNode {
    private SchemaType type;
    private String title;
    private String titleKey;
    private String description;
    private String descriptionKey;
    private Object defaultValue;
    protected URI originUri;
    protected String ref;
    private AstNode originAst;
    private String format = "";
    private String contentMediaType;
    private String dynamicAnchor;
    private boolean readOnly = false;

    private final SchemaNode metaSchema;

    protected final Map<String, SchemaNode> definitions = new HashMap<>();
    private final List<ValidationRule> rules = new ArrayList<>();
    private final Map<String, String> formatOptions = new HashMap<>();
    private final java.util.Map<String, Object> extensions = new java.util.HashMap<>();

    private final List<SchemaNode> allOf = new ArrayList<>();
    // TODO:
    // private final List<SchemaNode> anyOf = new ArrayList<>();
    // private final List<SchemaNode> oneOf = new ArrayList<>();

    public BaseSchemaNode(SchemaType type, SchemaNode metaSchema) {
        this.type = type;
        this.metaSchema = metaSchema;
    }

    public void setDynamicAnchor(String anchor) { this.dynamicAnchor = anchor; }
    @Override public String getDynamicAnchor() { return dynamicAnchor; }

    @Override public void addAllOf(SchemaNode node) { this.allOf.add(node); }
    @Override public List<SchemaNode> getAllOf() { return Collections.unmodifiableList(allOf); }

    public void setFormat(String format) { this.format = format; }
    @Override public String getFormat() { return format; }

    public void setFormatOption(String key, String value) { this.formatOptions.put(key, value); }
    @Override public String getFormatOption(String key) { return formatOptions.getOrDefault(key, ""); }

    // Abstract to be implemented by ObjectSchemaNode or returned empty by others
    @Override
    public abstract Map<String, SchemaNode> getProperties();

    // --- Common Logic ---
    public void addRule(ValidationRule rule) { this.rules.add(rule); }
    public void addDefinition(String key, SchemaNode node) { this.definitions.put(key, node); }

    public void setType(SchemaType type) { this.type = type; }
    public void setOriginUri(URI originUri) { this.originUri = originUri; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setTitleKey(String titleKey) { this.titleKey = titleKey; }
    public void setDescriptionKey(String descriptionKey) { this.descriptionKey = descriptionKey; }
    public void setRef(String ref) { this.ref = ref; }
    public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }

    public String getContentMediaType() {
        return contentMediaType;
    }

    public void setContentMediaType(String contentMediaType) {
        this.contentMediaType = contentMediaType;
    }

    //
    //
    //

    @Override
    public AstNode getOriginAst() {
        return originAst;
    }

    public void setOriginAst(AstNode originAst) {
        this.originAst = originAst;
    }

    @Override
    public boolean isReadOnly() {
        return readOnly;
    }

    @Override
    public void setReadOnly(boolean readOnly) {
        this.readOnly = readOnly;
    }

    @Override
    public void setExtension(String key, Object value) {
        extensions.put(key, value);
    }

    @Nullable
    @Override
    public Object getExtension(String key) {
        return extensions.get(key);
    }

    @Override public Map<String,Object> getExtensions() {
        return extensions;
    }

    @Override public SchemaType getType() { return type; }
    @Override public String getTitle() { return title; }
    @Override public String getTitleKey() { return titleKey; }
    @Override public String getDescription() { return description; }
    @Override public String getDescriptionKey() { return descriptionKey; }
    @Override public Object getDefaultValue() { return defaultValue; }
    @Override public Map<String, SchemaNode> getDefinitions() { return definitions; }
    @Override public List<ValidationRule> getRules() { return rules; }
    @Override public boolean isRef() { return ref != null; }
    @Override public String getRef() { return ref; }

    @Override
    public SchemaNode getProperty(String name) {
        return getProperties().get(name);
    }

    @Override
    public SchemaNode getDefinition(String name) {
        return getDefinitions().get(name);
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        for (ValidationRule rule : rules) {
            rule.validate(node, path, result);
        }
    }

    @Override
    public URI getOriginUri() {
        return originUri;
    }

    @Override
    public List<? extends AstNode> getChildren() {
        return originAst != null ? originAst.getChildren() : Collections.emptyList();
    }

    @Override
    public int getStartLine() {
        return originAst != null ? originAst.getStartLine() : -1;
    }

    @Override
    public int getStartColumn() {
        return originAst != null ? originAst.getStartColumn() : -1;
    }

    @Override
    public int getEndLine() {
        return originAst != null ? originAst.getEndLine() : -1;
    }

    @Override
    public int getEndColumn() {
        return originAst != null ? originAst.getEndColumn() : -1;
    }


    @Override
    public List<CommentAstNode> getComments() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getComments'");
    }

    @Override
    public SchemaNode getPropertySchema(String key) {
        // 1. Direct hit in the local properties map
        SchemaNode local = this.getProperties().get(key);
        if (local != null) return getResolved(local);

        // 2. Tunnel through Compositions
        // Draft 2020-12 uses allOf to pull in 'meta/validation' and 'meta/applicator'
        for (SchemaNode sub : getAllOf()) {
            SchemaNode resolvedSub = getResolved(sub);
            if (resolvedSub != null) {
                SchemaNode found = resolvedSub.getPropertySchema(key);
                if (found != null) return getResolved(found);
            }
        }

        return null;
    }

    private SchemaNode getResolved(SchemaNode schema) {
        if (schema instanceof LazySchemaNode lazy) {
            SchemaNode resolved = lazy.getResolved();
            return resolved;
        } else {
            return schema;
        }
    }

    @Override
    public SchemaNode getMetaSchema() {
        // If this IS the root Meta-Schema, it returns itself to close the loop.
        return metaSchema != null ? metaSchema : this;
    }
}