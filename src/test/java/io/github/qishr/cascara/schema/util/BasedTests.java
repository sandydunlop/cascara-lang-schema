package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.net.URI;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.schema.internal.CascaraSchemaCompiler;
import io.github.qishr.cascara.schema.internal.CascaraSchemaResolver;
import io.github.qishr.cascara.schema.structure.LazySchemaNode;
import io.github.qishr.cascara.schema.structure.ObjectSchemaNode;
import io.github.qishr.cascara.schema.structure.ScalarSchemaNode;
import io.github.qishr.cascara.schema.structure.SchemaNode;
import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.SchemaException;
import io.github.qishr.cascara.schema.SchemaType;

public class BasedTests extends SchemaIntegrationTestBase {

    @Test
    void testLazyNodeTriggeringResolution() throws SchemaException {
        CascaraSchemaResolver mockResolver = mock(CascaraSchemaResolver.class);
        AstNode mockAst = mock(AstNode.class);
        URI baseUri = URI.create("https://myserver.com/schema.json");
        SchemaNode mockMeta = mock(SchemaNode.class);

        // 1. Remove "common", add mockMeta
        SchemaNode expectedNode = new ScalarSchemaNode(SchemaType.STRING, mockMeta);

        // 2. Add mockMeta to the end of LazySchemaNode constructor
        LazySchemaNode lazy = new LazySchemaNode("common.json", mockResolver, null, baseUri, mockAst, null, mockMeta);

        // 3. Stub the 3-parameter version (same as your previous update)
        when(mockResolver.resolve(eq("common.json"), eq(lazy), any(DynamicScope.class)))
            .thenReturn(expectedNode);

        SchemaNode resolved = lazy.getResolved();

        verify(mockResolver).resolve(eq("common.json"), eq(lazy), any(DynamicScope.class));
        assertEquals(expectedNode, resolved);
        assertEquals(expectedNode.getOriginAst(), lazy.getOriginAst());
    }

    @Test
    void testInternalFragmentResolution() {
        CascaraSchemaResolver localResolver = new CascaraSchemaResolver();
        CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(localResolver);

        SimpleMapNode addrAst = new SimpleMapNode();
        addrAst.put("type", new SimpleScalarNode("string"));

        SimpleMapNode defsAst = new SimpleMapNode();
        defsAst.put("address", addrAst);

        SimpleMapNode rootAst = new SimpleMapNode();
        rootAst.put("$id", new SimpleScalarNode("file:///schema.json"));
        rootAst.put("definitions", defsAst);

        Schema compiled = compiler.compile(new SimpleDocument(rootAst));
        ObjectSchemaNode rootSchema = (ObjectSchemaNode) compiled.getRoot();

        SchemaNode result = localResolver.resolve("#/definitions/address", rootSchema);

        assertTrue(result instanceof ScalarSchemaNode, "Result should be the compiled scalar node");
        // Note: If you removed the name field entirely, you should assert on the type or a title instead.
        assertEquals(SchemaType.STRING, result.getType());
    }

    // TODO: This has been temporarily removed since CascaraSchemaResolver no longer
    // has a content loader that can be set.

    // @Test
    // void testRemoteToRemoteResolution() throws IOException {
    //     // 1. Constructor updated: Removed "anchor", added null for meta
    //     ObjectSchemaNode anchorNode = new ObjectSchemaNode(null);
    //     anchorNode.setOriginUri(URI.create("https://my-api.com/schemas/user.json"));

    //     String ref = "common/address.json";
    //     URI expectedUri = URI.create("https://my-api.com/schemas/common/address.json");

    //     when(mockLoader.getContent(eq(expectedUri)))
    //         .thenReturn(new ResourceContent("{}", null));

    //     // Execute
    //     resolver.resolve(ref, anchorNode);

    //     // Verify
    //     verify(mockLoader).getContent(expectedUri);
    // }

    // @Test
    // void testJsonSchemaOrgDraftResolution() throws Exception {
    //     // 1. Mocking the files
    //     mockRemoteFile("https://json-schema.org/draft/2020-12/schema",
    //         "{ \"$ref\": \"meta/core\" }");

    //     mockRemoteFile("https://json-schema.org/draft/2020-12/meta/core",
    //         "{ \"title\": \"Core Meta-Schema\" }");

    //     // 2. Constructor updated: Removed "root", added null for meta
    //     ObjectSchemaNode anchor = new ObjectSchemaNode(null);
    //     anchor.setOriginUri(URI.create("https://json-schema.org/draft/2020-12/schema"));

    //     // 3. Resolve (The return type is SchemaNode, not AstNode)
    //     SchemaNode result = resolver.resolve("meta/core", anchor);

    //     // 4. Assertions
    //     assertNotNull(result);
    //     assertEquals("Core vocabulary meta-schema", result.getTitle());
    // }

    // @Test
    // void verifyKeyPurity() throws Exception {
    //     mockRemoteFile("https://test.io/purity.json", "{ \"properties\": {} }");

    //     // 1. Constructor updated: Removed "anchor", added null for meta
    //     ObjectSchemaNode anchor = new ObjectSchemaNode(null);
    //     anchor.setOriginUri(URI.create("https://test.io/anchor.json"));

    //     // 2. Resolve
    //     SchemaNode result = resolver.resolve("purity.json", anchor);

    //     // 3. Extract AST for purity check
    //     AstNode rawAst = result.getOriginAst();

    //     if (rawAst instanceof MapAstNode map) {
    //         // Get the actual key object from the AST
    //         @SuppressWarnings("rawtypes")
    //         var entry = (MapEntryAstNode) map.getEntries().iterator().next();
    //         Object firstKey = entry.getKey();

    //         assertTrue(map.containsKey("properties"),
    //             "The AST map should contain the String 'properties'");

    //         // Verify the internal representation is a JsonScalarNode (from your JsonParser)
    //         assertTrue(firstKey instanceof JsonScalarNode,
    //             "Key should be a JsonScalarNode, but was: " + firstKey.getClass().getName());
    //     }
    // }
}