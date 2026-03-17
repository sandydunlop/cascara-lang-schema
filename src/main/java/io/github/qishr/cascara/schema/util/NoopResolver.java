package io.github.qishr.cascara.schema.util;

import java.net.URI;
import java.util.Map;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.api.SchemaResolver;
import io.github.qishr.cascara.schema.api.TypeAnalyzer;
import io.github.qishr.cascara.schema.ast.SchemaNode;

public class NoopResolver implements SchemaResolver {
    @Override
    public AstNode resolve(String ref, SchemaNode relativeTo) {
        return null; // no external schemas in tests
    }

    @Override
    public AstNode resolveFragment(String fragment, AstNode root) {
        return null; // no external schemas in tests
    }

    @Override
    public CompiledSchema getSchemaForClass(Class<?> clazz) {
        return null; // no external schemas in tests
    }

    @Override
    public CompiledSchema getSchema(URI uri) throws SchemaException {
        return null; // no external schemas in tests
    }

    @Override
    public Map<URI, CompiledSchema> getCachedSchemas() {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getCachedSchemas'");
    }

    @Override
    public void registerTypeAnalyzer(TypeAnalyzer ta) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'registerTypeAnalyzer'");
    }

    @Override
    public void registerSchema(URI uri, CompiledSchema compiled) {
        // Do nothing
    }
}