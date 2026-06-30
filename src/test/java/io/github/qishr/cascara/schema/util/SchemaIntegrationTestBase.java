package io.github.qishr.cascara.schema.util;

import org.junit.jupiter.api.BeforeEach;


public class SchemaIntegrationTestBase {
    // protected ContentLoader mockLoader;
    // protected SchemaParser JsonAstParser;
    protected SchemaResolver resolver;

    @BeforeEach
    void setup() {
        // mockLoader = mock(ContentLoader.class);

        // Bridge: extract the 'content' string from the record to feed the JsonAstParser
        // JsonAstParser = resource -> new JsonAstParser().parse(resource.content());

        // System Under Test
        resolver = new SchemaResolver();
    }

    // protected void mockRemoteFile(String uri, String contentStr) throws IOException {
    //     URI targetUri = URI.create(uri);

    //     // Match the record: (String content, ContentType contentType)
    //     // We pass null for ContentType unless the test explicitly validates it
    //     ResourceContent resource = new ResourceContent(contentStr, null);

    //     // Match the interface: getContent(URI)
    //     when(mockLoader.getContent(eq(targetUri))).thenReturn(resource);
    // }

    // protected void mockRemoteFile(String uri, String contentStr) throws IOException {
    //     // Match by the string representation of the URI to avoid instance-equality issues
    //     when(mockLoader.getContent(org.mockito.ArgumentMatchers.argThat(u ->
    //         u != null && u.toString().equals(uri)
    //     ))).thenReturn(new ResourceContent(contentStr, null));
    // }
}