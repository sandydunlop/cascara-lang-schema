package io.github.qishr.cascara.schema.internal;

import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.structure.*;

import java.net.URI;
import java.util.*;

public final class CompiledSchema implements Schema {

    private final SchemaNode root;
    private final URI originUri; // Store explicitly as the schema's identity
    private Map<String, SchemaNode> properties;
    private Map<String, SchemaNode> definitions;

    public CompiledSchema(URI originUri, SchemaNode root) {
        this.originUri = originUri;
        this.root = root;
    }

    public Collection<SchemaNode> getProperties() {
        return ensureProperties().values();
    }

    public Collection<SchemaNode> getDefinitions() {
        return ensureDefinitions().values();
    }

    public SchemaNode getRoot() {
        return root;
    }

    @Override
    public URI getOriginUri() {
        return originUri;
    }

    public URI getId() {
        return originUri;
    }

    @Override
    public URI getSchemaUri() {
        return URI.create("https://json-schema.org/draft/2020-12/schema");
    }

    /// Convenience method to find a definition by name.
    /// Returns null if the definition doesn't exist.
    public SchemaNode getDefinition(String name) {
        if (name == null) return null;
        return ensureDefinitions().get(name);
    }

    /// Convenience method to find a property by name.
    /// Only works if the root is an ObjectSchemaNode.
    public SchemaNode getProperty(String name) {
        if (name == null) return null;
        return ensureProperties().get(name);
    }

    private Map<String, SchemaNode> ensureProperties() {
        if (properties == null) {
            properties = new LinkedHashMap<>();
            if (root instanceof ObjectSchemaNode obj) {
                properties.putAll(obj.getProperties());
            }
        }
        return properties;
    }

    private Map<String, SchemaNode> ensureDefinitions() {
        if (definitions == null) {
            definitions = new LinkedHashMap<>();
            if (root != null) {
                definitions.putAll(root.getDefinitions());
            }
        }
        return definitions;
    }
}