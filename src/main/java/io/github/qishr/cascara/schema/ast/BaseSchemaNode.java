package io.github.qishr.cascara.schema.ast;

import io.github.qishr.cascara.common.lang.annotation.Nullable;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.schema.rule.ValidationRule;
import io.github.qishr.cascara.schema.util.ValidationResult;

import java.net.URI;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

public abstract class BaseSchemaNode implements SchemaNode {
    private String name;
    private SchemaType type;
    private String title;
    private String description;
    private Object defaultValue;
    protected URI originUri;
    protected String ref;
    private AstNode originAst;

    private final Map<String, SchemaNode> definitions = new HashMap<>();
    private final List<ValidationRule> rules = new ArrayList<>();
    private String format = "";
    private final Map<String, String> formatOptions = new HashMap<>();
    private boolean readOnly = false; // Default to false
    private final java.util.Map<String, Object> customHints = new java.util.HashMap<>();

    private final List<SchemaNode> allOf = new ArrayList<>();
    private final List<SchemaNode> anyOf = new ArrayList<>();
    private final List<SchemaNode> oneOf = new ArrayList<>();

    public BaseSchemaNode(String name, SchemaType type) {
        this.name = name;
        this.type = type;
    }

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

    public void setName(String name) { this.name = name; }
    public void setType(SchemaType type) { this.type = type; }
    public void setOriginUri(URI originUri) { this.originUri = originUri; }
    public void setTitle(String title) { this.title = title; }
    public void setDescription(String description) { this.description = description; }
    public void setRef(String ref) { this.ref = ref; }
    public void setDefaultValue(Object defaultValue) { this.defaultValue = defaultValue; }

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
    public void setCustomHint(String key, Object value) {
        customHints.put(key, value);
    }

    @Nullable
    @Override
    public Object getCustomHint(String key) {
        return customHints.get(key);
    }

    @Override public Map<String,Object> getCustomHints() {
        return customHints;
    }

    @Override public String getName() { return name; }
    @Override public SchemaType getType() { return type; }
    @Override public String getTitle() { return title; }
    @Override public String getDescription() { return description; }
    @Override public Object getDefaultValue() { return defaultValue; }
    @Override public Map<String, SchemaNode> getDefinitions() { return definitions; }
    @Override public List<ValidationRule> getRules() { return rules; }
    @Override public boolean isRef() { return ref != null; }
    @Override public String getRef() { return ref; }

    @Override
    public Optional<SchemaNode> getProperty(String name) {
        return Optional.ofNullable(getProperties().get(name));
    }

    @Override
    public Optional<SchemaNode> getDefinition(String name) {
        return Optional.ofNullable(getDefinitions().get(name));
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        for (ValidationRule rule : rules) {
            rule.validate(node, path, result);
        }
    }

    @Override
    public URI getUri() {
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

        // 2. Tunnel through Compositions (The Meta-Schema secret sauce)
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
            String message = String.format("Resolving %s -> %s", schema.getName(), schema.getRef());
            System.out.println(message);
            SchemaNode resolved = lazy.getResolved();
            return resolved;
        } else {
            return schema;
        }
    }
}