package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.util.CascaraSchemaCompiler;
import io.github.qishr.cascara.schema.util.CascaraSchemaResolver;

public class SchemaCompilerTests {
    StructuredDocument createTaskDoc() {

        SimpleMapNode properties = new SimpleMapNode();

        SimpleMapNode root = new SimpleMapNode();
        root.put("$id", new SimpleScalarNode(URI.create("cascara://synthetic/Task")));
        root.put("properties", properties);
        return new SimpleDocument(root);
    }

    @Test
    void test_name() {
        StructuredDocument doc = createTaskDoc();
        CascaraSchemaResolver resolver = new CascaraSchemaResolver(null, null);
        CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(resolver);

        CompiledSchema schema = compiler.compile(doc);
        String name = schema.getRoot().getName();

        assertEquals("Task", name);
    }
}
