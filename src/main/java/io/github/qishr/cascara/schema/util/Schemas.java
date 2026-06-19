package io.github.qishr.cascara.schema.util;

public class Schemas {
    private static SchemaResolver schemaResolver = new SchemaResolver();

    public static SchemaResolver getResolver() { return schemaResolver; }

    public static void setResolver(SchemaResolver resolver) {
        schemaResolver = resolver;
    }
}
