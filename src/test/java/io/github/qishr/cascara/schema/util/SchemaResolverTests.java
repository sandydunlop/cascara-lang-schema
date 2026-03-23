package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.lang.json.processor.JsonParser;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.api.SchemaCompiler;
import io.github.qishr.cascara.schema.ast.ArraySchemaNode;
import io.github.qishr.cascara.schema.ast.LazySchemaNode;
import io.github.qishr.cascara.schema.ast.ObjectSchemaNode;
import io.github.qishr.cascara.schema.ast.SchemaNode;

public class SchemaResolverTests {
    CascaraSchemaResolver resolver;

    @BeforeEach
    void setup() {
        resolver = new CascaraSchemaResolver(null, null);
    }

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

    @Test
    void shouldResolveInternalReferencesDuringInheritance() {
        String json = """
            {
                "$id": "cascara://test/items",
                "definitions": {
                  "bug": {
                    "type": "object",
                    "unevaluatedProperties": false,
                    "properties": {
                      "related_bugs": {
                        "type": "array",
                        "items": {
                          "$ref": "#/definitions/bug"
                        }
                      }
                    }
                  },
                  "security_bug": {
                    "allOf": [
                      {
                        "$ref": "#/definitions/bug"
                      }
                    ],
                    "properties": {
                      "cveId": {
                        "type": "string"
                      },
                    }
                  }
                }
              }
                """;

        JsonParser parser = new JsonParser();
        StructuredDocument doc = parser.parse(json);
        CascaraSchemaResolver resolver = new CascaraSchemaResolver(null, null);
        SchemaCompiler compiler = new CascaraSchemaCompiler(resolver);
        CompiledSchema schema = compiler.compile(doc);

        SchemaNode bug = schema.getDefinition("bug");
        if (bug instanceof ObjectSchemaNode obj) {
            SchemaNode relatedBugs = obj.getProperty("related_bugs");
            if (relatedBugs instanceof ArraySchemaNode array) {
                SchemaNode items = array.getItemSchema();
                if (items instanceof LazySchemaNode lazy) {
                    SchemaNode resolved = lazy.getResolved();
                    assertNotNull(resolved);
                }
            }
        }

        SchemaNode securityBug = schema.getDefinition("security_bug");
        if (securityBug instanceof ObjectSchemaNode obj) {
            SchemaNode relatedBugs = obj.getProperty("related_bugs");
            if (relatedBugs instanceof ArraySchemaNode array) {
                SchemaNode items = array.getItemSchema();
                if (items instanceof LazySchemaNode lazy) {
                    SchemaNode resolved = lazy.getResolved();
                    assertNotNull(resolved);
                }
            }
        }
    }

    @Test
    void shouldFailOnIncompatibleAstImplementation() {
        // A JsonMapNode is an AstNode, but NOT a StructuredDocument
        JsonParser parser = new JsonParser();
        AstNode rawJson = parser.parse("{ \"$id\": \"cascara://test\" }").getRoot();

        // This will trigger the ClassCastException in the current resolver
        // because it tries to cast rawJson to StructuredDocument
        resolver.registerAnchor(URI.create("cascara://test"), rawJson);
        assertDoesNotThrow(() -> resolver.getSchema(URI.create("cascara://test")));
    }

    @Test
    void shouldKeepCachesInSync() {
        URI uri = URI.create("cascara://test/sync");
        // Manually polluting nodeCache without a schema
        resolver.registerAnchor(uri, new SimpleMapNode());

        // This should return a valid CompiledSchema by triggering
        // a proper compilation, not a "fragment-root" or a null.
        assertNotNull(resolver.getSchema(uri), "SchemaCache must sync from NodeCache");
    }


}
