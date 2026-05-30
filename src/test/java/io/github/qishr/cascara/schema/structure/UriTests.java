package io.github.qishr.cascara.schema.structure;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.util.SchemaCompiler;
import io.github.qishr.cascara.schema.util.SchemaResolver;

public class UriTests {
    @Test
    void test_id() {
        URI uri = URI.create("cascara://core/schema-service/dynamic/cascara.schema/uri-tests");
        SimpleScalarNode id = new SimpleScalarNode(uri);
        SimpleMapNode root = new SimpleMapNode();
        root.put("$id", id);
        SimpleDocument doc = new SimpleDocument(root);

        SchemaResolver resolver = new SchemaResolver();
        SchemaCompiler compiler = new SchemaCompiler(resolver);

        Schema schema = compiler.compile(doc);

        URI schemaUri = schema.getOriginUri();
        assertEquals(uri, schemaUri);
    }
}
