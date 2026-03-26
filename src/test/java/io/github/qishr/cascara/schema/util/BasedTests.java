package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.IOException;
import java.net.URI;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.lang.json.ast.JsonScalarNode;
import io.github.qishr.cascara.schema.ast.LazySchemaNode;
import io.github.qishr.cascara.schema.ast.ObjectSchemaNode;
import io.github.qishr.cascara.schema.ast.ScalarSchemaNode;
import io.github.qishr.cascara.schema.ast.SchemaNode;
import io.github.qishr.cascara.schema.ast.SchemaType;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.SchemaException;
import io.github.qishr.cascara.schema.api.SchemaParser;
import io.github.qishr.cascara.common.content.ContentLoader;
import io.github.qishr.cascara.common.content.ResourceContent;

public class BasedTests extends SchemaIntegrationTestBase {

    // @Test
    // void testLazyNodeTriggeringResolution() throws SchemaException {
    //     CascaraSchemaResolver mockResolver = mock(CascaraSchemaResolver.class);
    //     URI baseUri = URI.create("https://myserver.com/schema.json");

    //     // 1. Create a dummy node to be returned
    //     SchemaNode expectedNode = new ScalarSchemaNode("common", SchemaType.STRING);

    //     // 2. Setup the Lazy node
    //     LazySchemaNode lazy = new LazySchemaNode("common.json", mockResolver, null, baseUri);

    //     // 3. THE FIX: Stub the resolver to return the dummy node
    //     when(mockResolver.resolve(eq("common.json"), eq(lazy))).thenReturn(expectedNode);

    //     // 4. Trigger resolution
    //     SchemaNode resolved = lazy.getResolved();

    //     // 5. Verify the call and the result
    //     verify(mockResolver).resolve(eq("common.json"), eq(lazy));
    //     assertEquals(expectedNode, resolved, "Lazy node should return the node provided by the resolver");
    // }
    // @Test
    // void testLazyNodeTriggeringResolution() throws SchemaException {
    //     CascaraSchemaResolver mockResolver = mock(CascaraSchemaResolver.class);
    //     // Add a mock for the AST identity
    //     AstNode mockAst = mock(AstNode.class);
    //     URI baseUri = URI.create("https://myserver.com/schema.json");

    //     // 1. Create a dummy node to be returned
    //     SchemaNode expectedNode = new ScalarSchemaNode("common", SchemaType.STRING);

    //     // 2. Setup the Lazy node with the new originAst parameter
    //     LazySchemaNode lazy = new LazySchemaNode("common.json", mockResolver, null, baseUri, mockAst, null);

    //     // 3. Stub the resolver
    //     when(mockResolver.resolve(eq("common.json"), eq(lazy))).thenReturn(expectedNode);

    //     // 4. Trigger resolution via getResolved()
    //     SchemaNode resolved = lazy.getResolved();

    //     // 5. Verify the result and that getOriginAst now delegates to the resolved node
    //     verify(mockResolver).resolve(eq("common.json"), eq(lazy));
    //     assertEquals(expectedNode, resolved);

    //     // This is the critical check for your fix:
    //     // Once resolved, it should return the expectedNode's AST, not the mockAst
    //     assertEquals(expectedNode.getOriginAst(), lazy.getOriginAst());
    // }
    @Test
    void testLazyNodeTriggeringResolution() throws SchemaException {
        CascaraSchemaResolver mockResolver = mock(CascaraSchemaResolver.class);
        AstNode mockAst = mock(AstNode.class);
        URI baseUri = URI.create("https://myserver.com/schema.json");

        SchemaNode expectedNode = new ScalarSchemaNode("common", SchemaType.STRING);

        // Pass null for the scope as before
        LazySchemaNode lazy = new LazySchemaNode("common.json", mockResolver, null, baseUri, mockAst, null);

        // UPDATE: Stub the 3-parameter version.
        // Even if you pass 'null' to the constructor, getResolved() creates a 'new DynamicScope(null)'
        when(mockResolver.resolve(eq("common.json"), eq(lazy), any(DynamicScope.class)))
            .thenReturn(expectedNode);

        SchemaNode resolved = lazy.getResolved();

        // UPDATE: Verify the 3-parameter version
        verify(mockResolver).resolve(eq("common.json"), eq(lazy), any(DynamicScope.class));

        assertEquals(expectedNode, resolved);
        assertEquals(expectedNode.getOriginAst(), lazy.getOriginAst());
    }

    @Test
    void testInternalFragmentResolution() {
        // 1. Setup the real infrastructure
        CascaraSchemaResolver localResolver = new CascaraSchemaResolver(mock(SchemaParser.class), mock(ContentLoader.class));
        CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(localResolver);

        // 2. Build the AST authentically using SimpleMapNode
        SimpleMapNode addrAst = new SimpleMapNode();
        addrAst.put("type", new SimpleScalarNode("string"));

        SimpleMapNode defsAst = new SimpleMapNode();
        defsAst.put("address", addrAst);

        SimpleMapNode rootAst = new SimpleMapNode();
        rootAst.put("$id", new SimpleScalarNode("file:///schema.json"));
        rootAst.put("definitions", defsAst);

        // 3. Compile properly so rootSchema is "live"
        CompiledSchema compiled = compiler.compile(new SimpleDocument(rootAst));
        ObjectSchemaNode rootSchema = (ObjectSchemaNode) compiled.getRoot();

        // 4. Resolve internal pointer - this will now succeed because
        // rootSchema.getDefinition("address") is populated.
        SchemaNode result = localResolver.resolve("#/definitions/address", rootSchema);

        // 5. Assert against the compiled node
        assertTrue(result instanceof ScalarSchemaNode, "Result should be the compiled scalar node");
        assertEquals("address", result.getName());
    }

    @Test
    void testRemoteToRemoteResolution() throws IOException {
        // Use a custom domain that isn't in your 'origin.properties'
        ObjectSchemaNode anchorNode = new ObjectSchemaNode("anchor");
        anchorNode.setOriginUri(URI.create("https://my-api.com/schemas/user.json"));

        // The Ref: a relative path to another custom schema
        String ref = "common/address.json";

        // Expected: https://my-api.com/schemas/common/address.json
        URI expectedUri = URI.create("https://my-api.com/schemas/common/address.json");

        when(mockLoader.getContent(eq(expectedUri)))
            .thenReturn(new ResourceContent("{}", null));

        // Execute
        resolver.resolve(ref, anchorNode);

        // Verify the mock was actually used
        verify(mockLoader).getContent(expectedUri);
    }

    @Test
    void testJsonSchemaOrgDraftResolution() throws Exception {
        // 1. Mock the main draft schema
        mockRemoteFile("https://json-schema.org/draft/2020-12/schema",
            "{ \"$ref\": \"meta/core\" }");

        // 2. Mock the meta/core schema it points to
        mockRemoteFile("https://json-schema.org/draft/2020-12/meta/core",
            "{ \"title\": \"Core Meta-Schema\" }");

        // 3. Create the anchor
        ObjectSchemaNode anchor = new ObjectSchemaNode("root");
        anchor.setOriginUri(URI.create("https://json-schema.org/draft/2020-12/schema"));

        // 4. Resolve the relative ref
        AstNode result = resolver.resolve("meta/core", anchor);

        // 5. Assertions
        assertNotNull(result);
        assertTrue(result instanceof SchemaNode, "Result should be compiled into a SchemaNode");
        assertEquals("Core vocabulary meta-schema", ((SchemaNode)result).getTitle());
    }

    @Test
    void verifyKeyPurity() throws Exception {
        mockRemoteFile("https://test.io/purity.json", "{ \"properties\": {} }");
        ObjectSchemaNode anchor = new ObjectSchemaNode("anchor");
        anchor.setOriginUri(URI.create("https://test.io/anchor.json"));

        // This returns a SchemaNode (specifically ObjectSchemaNode)
        SchemaNode result = (SchemaNode) resolver.resolve("purity.json", anchor);

        // Reach back into the raw AST that was used to build this SchemaNode
        AstNode rawAst = result.getOriginAst();

        if (rawAst instanceof MapAstNode map) {
            Object firstKey = map.keys().iterator().next();

            // This is the real test of the JsonParser!
            assertTrue(map.containsKey("properties"),
                "The AST map should contain the String 'properties', but found: " + firstKey);

            // Also verify the type of the key isn't a Node object
            assertTrue(firstKey instanceof JsonScalarNode,
                "Key should be a JsonScalarNode, but was: " + firstKey.getClass().getName());
        }
    }
}