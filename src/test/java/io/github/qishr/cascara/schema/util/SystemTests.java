package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.util.Map;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.lang.json.JsonDocument;
import io.github.qishr.cascara.lang.json.processor.JsonParser;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.SchemaType;
import io.github.qishr.cascara.schema.ast.ObjectSchemaNode;
import io.github.qishr.cascara.schema.ast.SchemaNode;

public class SystemTests {
    CascaraSchemaResolver resolver;
    CascaraSchemaCompiler compiler;

    @BeforeEach
    void setup() {
        resolver = new CascaraSchemaResolver(null, null);
        compiler = new CascaraSchemaCompiler(resolver);
    }

    @Test
    void task_shouldInheritPropertiesFromItem() {
        // 1. Setup the CEMA Meta-Schema in the provider/cache
        // (Assuming your test harness pre-loads the cascara://core/.../cema-meta)

        String json = """
            {
                "$id": "cascara://core/test/entities",
                "$schema": "cascara://core/schema-service/cascara.persistence.cema/cema-meta",
                "$defs": {
                  "tag": {
                    "type": "object",
                    "properties": {
                      "name": {
                        "type": "string"
                      }
                    }
                  },
                  "item": {
                    "type": "object",
                    "properties": {
                      "id": {
                        "type": "integer"
                      },
                      "docId": {
                        "type": "string"
                      }
                    }
                  },
                  "task": {
                    "allOf": [
                      {
                        "$ref": "#/$defs/item"
                      }
                    ],
                    "type": "object",
                    "properties": {
                      "name": {
                        "type": "string"
                      },
                      "status": {
                        "$ref": "#/$defs/tag",
                        "x-tracked": true
                      }
                    }
                  }
                }
              }
            """;

        JsonDocument doc = new JsonParser().parse(json);
        CompiledSchema compiled = compiler.compile(doc);

        // 2. Resolve the "task" node
        ObjectSchemaNode taskNode = (ObjectSchemaNode) resolver.resolve("#/$defs/task", compiled.getRoot());

        // 3. Verify Property Merger
        Map<String, SchemaNode> props = taskNode.getProperties();

        // Assert inherited properties
        assertNotNull(props.get("id"), "Should inherit 'id' from item");
        assertNotNull(props.get("docId"), "Should inherit 'docId' from item");

        // Assert local properties
        assertNotNull(props.get("name"), "Should have local 'name' property");
        assertNotNull(props.get("status"), "Should have local 'status' property");

        // 4. Verify CEMA Vocabulary Retention
        SchemaNode statusNode = props.get("status");
        Object xTracked = statusNode.getExtension("x-tracked");

        assertNotNull(xTracked, "CEMA extension 'x-tracked' should survive the compile/merge");
        assertEquals(true, xTracked, "x-tracked should be Boolean true");

        // 5. Verify Inherited Property Details
        SchemaNode inheritedIdNode = props.get("id");
        assertNotNull(inheritedIdNode, "The 'id' property must be present in 'task'");

        // Ensure it's still an IntegerSchemaNode (or has the correct type attribute)
        assertEquals(SchemaType.INTEGER, inheritedIdNode.getType(), "Inherited 'id' should still be an integer");

        // 6. Verify Deep Extension Merger (Optional but recommended)
        // If 'item' had an 'x-storage' or 'x-indexed' tag, we'd check that here too.

    }
}
