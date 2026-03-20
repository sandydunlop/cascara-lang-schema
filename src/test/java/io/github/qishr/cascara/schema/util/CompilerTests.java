package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertNotNull;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.json.JsonDocument;
import io.github.qishr.cascara.lang.json.processor.JsonParser;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.ast.ObjectSchemaNode;
import io.github.qishr.cascara.schema.ast.SchemaNode;

public class CompilerTests {
    @Test
    void compiler_shouldPreserveCustomMetadata() {
        String json = """
        {
        "$id": "cascara://test",
        "definitions": {
            "item": { "type": "object", "properties": { "status": { "type": "string", "x-tracked": true } } },
            "task": { "x-parent": "item", "type": "object", "properties": { "name": { "type": "string" } } }
        }
        }
        """;
        JsonParser parser = new JsonParser();
        JsonDocument doc = parser.parse(json);

        CascaraSchemaResolver resolver = new CascaraSchemaResolver(null, null);
        CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(resolver);
        CompiledSchema schema = compiler.compile(doc);

        ObjectSchemaNode taskNode = (ObjectSchemaNode) schema.getDefinition("task");

        // This is likely where your current failure is:
        assertNotNull(taskNode.getExtension("x-parent"), "Compiler dropped 'parent' keyword!");
        ObjectSchemaNode item = (ObjectSchemaNode)schema.getDefinition("item");
        SchemaNode statusNode = item.getProperty("status");
        assertNotNull(statusNode.getExtension("x-tracked"), "Compiler dropped 'x-tracked' hint!");
    }
}
