package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;

import java.net.URI;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
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
  @DisplayName("Should handle JSON-backed StructuredDocument without ClassCastException")
  void shouldHandleJsonBackedDocument() {
      // 1. Setup a JSON document
      String json = "{ \"$id\": \"cascara://test/json\", \"type\": \"object\" }";
      JsonParser parser = new JsonParser();
      StructuredDocument doc = parser.parse(json);
      URI uri = URI.create("cascara://test/json");

      // 2. Register it as a compiled schema (simulating what the compiler does)
      CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(resolver);
      CompiledSchema compiled = compiler.compile(doc, uri);
      resolver.registerSchema(uri, compiled);

      // 3. Verify retrieval works through the correct interface
      assertDoesNotThrow(() -> {
          CompiledSchema retrieved = resolver.getSchema(uri);
          assertNotNull(retrieved);
          assertEquals(uri, retrieved.getRoot().getOriginUri());
      });
  }

  @Test
  @DisplayName("Should maintain synchronization between SchemaDoc and SchemaNode caches")
  void shouldKeepCachesInSync() {
      URI docUri = URI.create("cascara://test/sync");
      String json = """
          {
            "$id": "cascara://test/sync",
            "definitions": {
              "item": { "$anchor": "main-item", "type": "string" }
            }
          }
          """;

      JsonParser parser = new JsonParser();
      StructuredDocument doc = parser.parse(json);
      CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(resolver);

      // Compiling the document should populate the Resolver's caches
      CompiledSchema compiled = compiler.compile(doc, docUri);
      resolver.registerSchema(docUri, compiled);

      // Verify the document cache
      assertNotNull(resolver.getSchema(docUri), "Document cache missing entry");

      // Verify fragment resolution works through the schemaDocCache fallback
      // even if the specific node wasn't manually registered in schemaNodeCache
      assertDoesNotThrow(() -> {
          SchemaNode resolved = resolver.resolve("#main-item", compiled.getRoot());
          assertNotNull(resolved, "Resolution should find anchor via AST walk if not in NodeCache");
      });
  }


}
