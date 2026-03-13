package io.github.qishr.cascara.lang.schema.util;

import java.util.HashMap;
import java.util.Map;
import java.util.Set;

import io.github.qishr.cascara.lang.schema.CompiledSchema;

public final class SchemaRegistry {

    private final Map<String, CompiledSchema> schemas = new HashMap<>();

    public SchemaRegistry() {}

    public void register(CompiledSchema schema) {
        schemas.put(schema.getName(), schema);
    }

    public CompiledSchema get(String name) {
        return schemas.get(name);
    }

    public Set<String> getSchemaNames() {
        return schemas.keySet();
    }

    public CompiledSchema getSchema(Class<?> clazz) {
        return schemas.get(clazz.getSimpleName());
    }

    public CompiledSchema getSchema(String name) {
        return schemas.get(name);
    }
}
