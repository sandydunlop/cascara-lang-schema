package io.github.qishr.cascara.schema;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.schema.api.SchemaResolver;
import io.github.qishr.cascara.schema.api.TypeAnalyzer;
import io.github.qishr.cascara.schema.ast.SchemaNode;
import io.github.qishr.cascara.schema.util.SchemaException;

public class TestResolver implements SchemaResolver {
    private final Map<String, SchemaNode> docs = new HashMap<>();

    public void register(URI uri, SchemaNode doc) {
        docs.put(uri.toString(), doc);
    }

    @Override
    public SchemaNode resolve(String ref, SchemaNode relativeTo) {
        return docs.get(ref);
    }

    @Override
    public AstNode resolveFragment(String fragment, AstNode root) {
        return null; // not needed for this test
    }

    @Override
    public CompiledSchema getSchemaForClass(Class<?> clazz) {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSchemaForClass'");
    }

    @Override
    public CompiledSchema getSchema(URI uri) throws SchemaException {
        // TODO Auto-generated method stub
        throw new UnsupportedOperationException("Unimplemented method 'getSchema'");
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
