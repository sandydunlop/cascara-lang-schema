package io.github.qishr.cascara.schema.util;

import java.net.URI;
import java.util.List;
import java.util.Map;

import io.github.qishr.cascara.common.service.ServiceProvider;
import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.SchemaException;
import io.github.qishr.cascara.schema.structure.SchemaNode;

public interface SchemaResolver extends ServiceProvider {

    SchemaNode resolve(String ref, SchemaNode relativeTo) throws SchemaException;
    SchemaNode resolve(String ref, SchemaNode relativeTo, DynamicScope scope) throws SchemaException;
    DynamicScope getCurrentScope();

    Schema getSchema(URI uri) throws SchemaException;
    Schema getSchemaForClass(Class<?> clazz);
    Schema getSchemaForClass(Class<?> clazz, List<TypeAnalyzer> typeAnalyzers);
    Map<URI, Schema> getCachedSchemas();

    void registerSchema(URI uri, Schema compiled);
    void registerSchemaNode(URI uri, SchemaNode node);

}