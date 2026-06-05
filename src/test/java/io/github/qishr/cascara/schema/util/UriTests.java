package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.reference.ReferenceMapNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceScalarNode;
import io.github.qishr.cascara.schema.Schema;

public class UriTests {
    @Test
    void test_id() {
        URI uri = URI.create("cascara://core/schema-service/dynamic/cascara.schema/uri-tests");
        ReferenceScalarNode id = new ReferenceScalarNode(uri);
        ReferenceMapNode root = new ReferenceMapNode();
        root.put("$id", id);

        SchemaResolver resolver = new SchemaResolver();
        SchemaCompiler compiler = new SchemaCompiler(resolver);

        Schema schema = compiler.compile(root, uri);

        URI schemaUri = schema.getOriginUri();
        assertEquals(uri, schemaUri);
    }
}
