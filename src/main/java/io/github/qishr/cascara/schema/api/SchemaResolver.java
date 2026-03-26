package io.github.qishr.cascara.schema.api;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.ast.SchemaNode;
import io.github.qishr.cascara.schema.util.SchemaException;

public interface SchemaResolver {

    SchemaNode resolve(String ref, SchemaNode relativeTo) throws SchemaException;

    CompiledSchema getSchemaForClass(Class<?> clazz) throws SchemaException;
    CompiledSchema getSchemaForClass(Class<?> clazz, List<TypeAnalyzer> typeAnalyzers) throws SchemaException;

    CompiledSchema getSchema(URI uri) throws SchemaException;
    Map<URI, CompiledSchema> getCachedSchemas();

    void registerSchema(URI uri, CompiledSchema compiled);
    void registerSchemaNode(URI uri, SchemaNode node);
}