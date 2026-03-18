package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.schema.CompiledSchema;

public class SchemaResolverTests {
    StructuredDocument createTagDoc() {
        // SimpleScalarNode id =
        SimpleMapNode root = new SimpleMapNode();
        root.put("$id", new SimpleScalarNode(URI.create("cascara://synthetic/Tag")));
        return new SimpleDocument(root);
    }

    StructuredDocument createTaskDoc() {
        SimpleMapNode items = new SimpleMapNode();
        items.put("$ref", new SimpleScalarNode(URI.create("cascara://synthetic/Tag")));

        SimpleMapNode tags = new SimpleMapNode();
        tags.put("type", new SimpleScalarNode("array"));
        tags.put("items", items);

        SimpleMapNode properties = new SimpleMapNode();
        properties.put("tags", tags);

        SimpleMapNode root = new SimpleMapNode();
        root.put("$id", new SimpleScalarNode(URI.create("cascara://synthetic/Task")));
        root.put("properties", properties);
        return new SimpleDocument(root);
    }

    @Test
    void test_synthetic_uri() {

        StructuredDocument tagDoc = createTagDoc();
        StructuredDocument taskDoc = createTaskDoc();

        CascaraSchemaResolver resolver = new CascaraSchemaResolver(null, null);
        CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(resolver);

        compiler.compile(tagDoc); // This automatically registers it with the resolver
        CompiledSchema taskSchema = compiler.compile(taskDoc);
        assertNotNull(taskSchema);
    }
}
