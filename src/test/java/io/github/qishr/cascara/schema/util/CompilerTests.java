package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.nio.charset.StandardCharsets;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.json.JsonDocument;
import io.github.qishr.cascara.lang.json.processor.JsonParser;
import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.structure.LazySchemaNode;
import io.github.qishr.cascara.schema.structure.ObjectSchemaNode;
import io.github.qishr.cascara.schema.structure.SchemaNode;

public class CompilerTests {
    @Test
    void compiler_shouldPreserveCustomMetadata() {
        String json = """
        {
        "$id": "cascara://core/schema-service/dynamic/cascara.schema/compiler-tests",
        "definitions": {
            "item": { "type": "object", "properties": { "status": { "type": "string", "x-tracked": true } } },
            "task": { "x-parent": "item", "type": "object", "properties": { "name": { "type": "string" } } }
        }
        }
        """;
        JsonParser parser = new JsonParser();
        JsonDocument doc = parser.parse(json);

        SchemaResolver resolver = new SchemaResolver();
        SchemaCompiler compiler = new SchemaCompiler(resolver);
        Schema schema = compiler.compile(doc);

        ObjectSchemaNode taskNode = (ObjectSchemaNode) schema.getDefinition("task");

        // This is likely where your current failure is:
        assertNotNull(taskNode.getExtension("x-parent"), "Compiler dropped 'parent' keyword!");
        ObjectSchemaNode item = (ObjectSchemaNode)schema.getDefinition("item");
        SchemaNode statusNode = item.getProperty("status");
        assertNotNull(statusNode.getExtension("x-tracked"), "Compiler dropped 'x-tracked' hint!");
    }

    @Test
    void compiler_test_01() {
        String json = getStringResource("/io/github/qishr/cascara/schema/util/schema-01.json");

        JsonParser parser = new JsonParser();
        JsonDocument doc = parser.parse(json);

        SchemaResolver resolver = new SchemaResolver();
        SchemaCompiler compiler = new SchemaCompiler(resolver);
        Schema schema = compiler.compile(doc);

        ObjectSchemaNode taskNode = (ObjectSchemaNode) schema.getDefinition("task");

        SchemaNode status = taskNode.getProperty("status");
        if (status instanceof LazySchemaNode lazy) {
            SchemaNode resolved = lazy.getResolved();
            assertNotNull(resolved);
            if (resolved instanceof ObjectSchemaNode resolvedNode) {
                SchemaNode order = resolvedNode.getProperty("order");
                assertNotNull(order);
            }
        }
    }


    public static String getStringResource(String path) {
        try (var is = CompilerTests.class.getResourceAsStream(path)) {
            if (is == null) throw new IllegalArgumentException("Resource not found: " + path);
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        } catch (Exception e) {
            throw new RuntimeException("Failed to read resource: " + path, e);
        }
    }
}
