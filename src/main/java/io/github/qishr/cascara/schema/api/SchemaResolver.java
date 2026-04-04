package io.github.qishr.cascara.schema.api;

import java.net.URI;
import java.util.Map;

import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.SchemaException;
import io.github.qishr.cascara.schema.ast.SchemaNode;
import io.github.qishr.cascara.schema.util.DynamicScope;

public interface SchemaResolver {

    SchemaNode resolve(String ref, SchemaNode relativeTo) throws SchemaException;
    SchemaNode resolve(String ref, SchemaNode relativeTo, DynamicScope scope) throws SchemaException;
    DynamicScope getCurrentScope();

    CompiledSchema getSchema(URI uri) throws SchemaException;
    Map<URI, CompiledSchema> getCachedSchemas();

    void registerSchema(URI uri, CompiledSchema compiled);
    void registerSchemaNode(URI uri, SchemaNode node);

}