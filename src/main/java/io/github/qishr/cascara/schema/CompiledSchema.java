package io.github.qishr.cascara.schema;

import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.CommentAstNode;
import io.github.qishr.cascara.schema.ast.*;
import io.github.qishr.cascara.schema.util.SchemaException;

import java.net.URI;
import java.util.*;
import java.util.Map.Entry;

public final class CompiledSchema implements StructuredDocument {

    private final String name;
    private final ObjectSchemaNode root;
    private Map<String, SchemaNode> properties;
    private Map<String, SchemaNode> definitions;

    public CompiledSchema(String name, ObjectSchemaNode root) {
        this.name = name;
        this.root = root;
    }

    //
    // Properties
    //

    private Map<String, SchemaNode> ensureProperties() {
        if (properties == null) {
            properties = new LinkedHashMap<>();
            if (root != null) {
                for (Entry<String, SchemaNode> prop : root.getProperties().entrySet()) {
                    properties.put(prop.getKey(), prop.getValue());
                }
            }
        }
        return properties;
    }

    public Collection<SchemaNode> getProperties() {
        return ensureProperties().values();
    }

    public SchemaNode getProperty(String name) {
        return ensureProperties().get(name);
    }

    //
    // Definitions
    //

    private Map<String, SchemaNode> ensureDefinitions() {
        if (definitions == null) {
            definitions = new LinkedHashMap<>();
            if (root != null) {
                for (Entry<String, SchemaNode> prop : root.getDefinitions().entrySet()) {
                    definitions.put(prop.getKey(), prop.getValue());
                }
            }
        }
        return definitions;
    }

    public Collection<SchemaNode> getDefinitions() {
        return ensureDefinitions().values();
    }

    public SchemaNode getDefinition(String name) {
        return ensureDefinitions().get(name);
    }

    //
    //
    //

    public String getName() {
        return name;
    }

    public ObjectSchemaNode getRoot() {
        return root;
    }

    @Override public int getStartLine() { return 0; }
    @Override public int getStartColumn() {return 0; }
    @Override public int getEndLine() {return 0; }
    @Override public int getEndColumn() {return 0; }

    @Override public URI getOriginUri() {
        return root == null ? null : root.getOriginUri();
    }

    public URI getId() {
        return root == null ? null : root.getOriginUri();
    }

    public void setId(URI id) {
        if (root == null) throw new SchemaException("CompiledSchema has no root", "");
        root.setOriginUri(id);
    }

    @Override
    public List<? extends AstNode> getChildren() {
        return root == null ? List.of() : List.of(root);
    }

    @Override
    public List<CommentAstNode> getComments() {
        return List.of();
    }

    @Override
    public URI getSchemaUri() {
        return URI.create("https://json-schema.org/draft/2020-12/schema");
    }
}
