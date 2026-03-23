package io.github.qishr.cascara.schema.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;
import java.util.Properties;

import io.github.qishr.cascara.common.content.ContentLoader;
import io.github.qishr.cascara.common.content.ResourceContent;
import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.lang.json.processor.JsonParser;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.SchemaKeyword;
import io.github.qishr.cascara.schema.api.SchemaCompiler;
import io.github.qishr.cascara.schema.api.SchemaParser;
import io.github.qishr.cascara.schema.api.SchemaResolver;
import io.github.qishr.cascara.schema.api.TypeAnalyzer;
import io.github.qishr.cascara.schema.ast.ArraySchemaNode;
import io.github.qishr.cascara.schema.ast.BaseSchemaNode;
import io.github.qishr.cascara.schema.ast.ObjectSchemaNode;
import io.github.qishr.cascara.schema.ast.SchemaNode;
import io.github.qishr.cascara.schema.util.CascaraSchemaResolver;

public class CascaraSchemaResolver implements SchemaResolver {
    private static final String SCHEMA_SERVICE_URI = "cascara://core/schema-service/";

    private final ContentLoader contentLoaderService;
    private final SchemaParser parserService;
    private final ClassSchemaGenerator generator;

    private final Map<URI, AstNode> nodeCache = new HashMap<>();
    private final Map<URI, CompiledSchema> schemaCache = new HashMap<>();

    public CascaraSchemaResolver(SchemaParser parserService, ContentLoader contentLoaderService) {
        this.contentLoaderService = contentLoaderService;
        this.parserService = parserService;
        this.generator = new ClassSchemaGenerator();
        loadBuiltInMetaSchemas();
    }

    @Override
    public void registerTypeAnalyzer(TypeAnalyzer ta) {
        generator.registerTypeAnalyzer(ta);
    }

    @Override
    public void registerSchema(URI uri, CompiledSchema compiled) {
        schemaCache.put(uri, compiled);
    }

    @Override
    public void registerAnchor(URI uri, AstNode node) {
        nodeCache.put(uri, node);
    }

    @Override
    public CompiledSchema getSchemaForClass(Class<?> clazz) throws SchemaException {
        URI originUri = getSchemaUriForClass(clazz);
        CompiledSchema schema = schemaCache.get(originUri);
        if (schema == null) {
            schema = generateSchemaForClass(clazz);
            schemaCache.put(originUri, schema);
        }

        return schema;
    }

    @Override
    public CompiledSchema getSchema(URI uri) throws SchemaException {
        System.out.println("SchemaResolver.getSchema: " + uri);

        CompiledSchema schema = schemaCache.get(uri);
        if (schema != null) return schema;

        System.out.println("SchemaResolver.getSchema: nto found in cache. Calling getOrLoadAst.");

        // 1. Load the document (this handles content fetching and parsing)
        // We cast to StructuredDocument because that's what parseContent returns
        StructuredDocument doc = (StructuredDocument)getOrLoadAst(uri);

        // 2. Compile using the standard interface method
        SchemaCompiler compiler = new CascaraSchemaCompiler(this);
        schema = compiler.compile(doc, uri);

        // 3. Cache the result
        schemaCache.put(uri, schema);

        return schema;
    }

    @Override
    public Map<URI, CompiledSchema> getCachedSchemas() {
        return schemaCache;
    }

    @Override
    public SchemaNode resolve(String ref, SchemaNode relativeTo) throws SchemaException {
        try {
            URI baseUri = relativeTo.getOriginUri();
            URI targetUri = baseUri.resolve(ref);

            SchemaCompiler compiler = new CascaraSchemaCompiler(this);
            URI docUri = stripFragment(targetUri);

            // Instant Lookup (Anchors or already cached nodes)
            AstNode targetAst = nodeCache.get(targetUri);

            if (targetAst == null) {
                String fragment = targetUri.getFragment();
                if (isSameDocument(baseUri, targetUri)) {
                    AstNode rootAst = relativeTo.getOriginAst();
                    if (fragment != null && !fragment.isEmpty()) {
                        // Strip the '#' if present before passing to resolveFragment
                        String path = fragment.startsWith("#") ? fragment.substring(1) : fragment;
                        targetAst = resolveFragment(path, rootAst);
                    } else {
                        targetAst = rootAst;
                    }
                }
                else {
                    // Different document
                    CompiledSchema externalSchema = getSchema(docUri);
                    AstNode root = externalSchema.getRoot().getOriginAst();
                    if (fragment != null && !fragment.isEmpty()) {
                        if (fragment.startsWith("/")) {
                            // It's a JSON Pointer: resolve against the root we just got
                            targetAst = resolveFragment(fragment, root);
                        } else {
                            // It's a plain anchor (#item)
                            // Check the nodeCache. If it's null, the compiler either
                            // skipped registration or used a different URI key.
                            targetAst = nodeCache.get(targetUri);

                            // Fallback: If the cache missed, we can try to find it in the root
                            if (targetAst == null) {
                                targetAst = findNodeByAnchor(root, fragment);
                            }
                        }
                    } else {
                        targetAst = root;
                    }
                }
            }

            if (targetAst == null) {
                throw new SchemaException("Could not resolve reference: " + ref, targetUri.toString());
            }

            if (targetAst instanceof StructuredDocument structuredDoc) {
                return compiler.compile(structuredDoc, docUri).getRoot();
            } else if (targetAst instanceof MapAstNode map) {
                // CompiledSchema fullSchema = getSchema(docUri);
                // if (fullSchema != null) {
                //     // Search the already-compiled tree for the node matching this AST
                //     SchemaNode existingNode = findNodeByAst(fullSchema.getRoot(), map);
                //     if (existingNode != null) {
                //         return existingNode;
                //     }
                // }

                // Fallback only if the document hasn't been compiled yet
                // (though getSchema usually triggers compilation)
                return compiler.compileSubSchema(map, docUri);
            }

            throw new SchemaException("Unsupported AST type: " + targetAst.getClass(), ref);

        } catch (Exception e) {
            if (e instanceof SchemaException se) throw se;
            throw new SchemaException("Resolution failed: " + e.getMessage(), e, ref);
        }
    }

    private SchemaNode findNodeByAst(SchemaNode root, AstNode targetAst) {
        if (root == null || targetAst == null) return null;

        // 1. Check if this is the node we're looking for
        if (root instanceof BaseSchemaNode base && base.getOriginAst() == targetAst) {
            return root;
        }

        // 2. Search Properties if it's an Object
        if (root instanceof ObjectSchemaNode obj) {
            for (SchemaNode prop : obj.getProperties().values()) {
                SchemaNode found = findNodeByAst(prop, targetAst);
                if (found != null) return found;
            }
            // Also check definitions!
            for (SchemaNode def : obj.getDefinitions().values()) {
                SchemaNode found = findNodeByAst(def, targetAst);
                if (found != null) return found;
            }
        }

        // 3. Search Item Template if it's an Array
        if (root instanceof ArraySchemaNode arr) {
            SchemaNode found = findNodeByAst(arr.getItemSchema(), targetAst);
            if (found != null) return found;
        }

        // 4. Search Composition (allOf, anyOf, oneOf)
        for (SchemaNode sub : root.getAllOf()) {
            SchemaNode found = findNodeByAst(sub, targetAst);
            if (found != null) return found;
        }

        return null;
    }

    private AstNode findNodeByAnchor(AstNode root, String anchor) {
        if (root instanceof MapAstNode map) {
            // 1. Check if this specific node is the target
            String id = map.getString(SchemaKeyword.ID.string());
            String nodeAnchor = map.getString(SchemaKeyword.ANCHOR.string());

            if (anchor.equals(id) || anchor.equals(nodeAnchor)) {
                return map;
            }

            // 2. Recurse into children
            for (Object child : map.getEntries()) {
                if (child instanceof MapAstNode || child instanceof SequenceAstNode) {
                    AstNode found = findNodeByAnchor((AstNode)child, anchor);
                    if (found != null) return found;
                }
            }
        } else if (root instanceof SequenceAstNode seq) {
            // Recurse into array elements
            for (Object item : seq.getElements()) {
                AstNode found = findNodeByAnchor((AstNode)item, anchor);
                if (found != null) return found;
            }
        }
        return null;
    }

    @Override
    public AstNode resolveFragment(String path, AstNode root) throws SchemaException {
        AstNode node = SchemaUtils.resolveFragment(root, path);

        // Infer the fragment's name from its path
        String name = extractName(path);
        if (name != null && node instanceof SimpleMapNode map) {
            map.put("name", new SimpleScalarNode(name));
        }

        return node;
    }

    public URI getSchemaUriForClass(Class<?> clazz) {
        String origin = SCHEMA_SERVICE_URI + clazz.getName();
        URI originUri = URI.create(origin);
        return originUri;
    }

    //
    //
    //

    private AstNode getOrLoadAst(URI docUri) {
        System.out.println("SchemaResolver.getOrLoadAst: " + docUri);

        // Check nodeCache first (which stores both documents and anchors)
        AstNode existing = nodeCache.get(docUri);
        if (existing != null) return existing;

        // 2. STOPS THE LOOP:
        // If it's a cascara URI and it's not in the cache, it's a bug in the
        // bootstrap/compilation order. Do NOT ask the ContentLoader.
        if ("cascara".equals(docUri.getScheme())) {
            throw new SchemaException(
                "Internal schema not found in Resolver cache. " +
                "Ensure the schema is registered before resolution.", docUri.toString()
            );
        }

        System.out.println("SchemaResolver.getOrLoadAst: Not found in cache. calling contentLoaderService.getContent: " + docUri);

        try {
            ResourceContent content = contentLoaderService.getContent(docUri);
            StructuredDocument doc = parseContent(content);
            // Cache the root document node
            nodeCache.put(docUri, doc);
            return doc;
        } catch (IOException e) {
            throw new SchemaException("Could not load AST for URI", e, docUri.toString());
        }
    }

    private String extractName(String path) {
        String name = null;
        String[] segments = path.split("/");
        for (String segment : segments) {
            if (segment.isEmpty()) continue;
            name = segment;
        }
        return name;
    }

    private CompiledSchema generateSchemaForClass(Class<?> clazz) throws SchemaException {
        URI originUri = getSchemaUriForClass(clazz);

        SchemaCompiler compiler = new CascaraSchemaCompiler(this);
        StructuredDocument schemaDoc = generator.generate(clazz);

        // TODO: Logging
        // AstUtil.printAst(schemaDoc.getRoot(), 0);

        CompiledSchema compiledSchema = compiler.compile(schemaDoc, originUri);

        return compiledSchema;
    }

    private boolean isSameDocument(URI base, URI target) {
        // Compare scheme, authority, and path, ignoring the fragment
        return Objects.equals(base.getScheme(), target.getScheme()) &&
               Objects.equals(base.getAuthority(), target.getAuthority()) &&
               Objects.equals(base.getPath(), target.getPath());
    }

    private URI stripFragment(URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), null, null);
        } catch (Exception e) {
            throw new SchemaException("Failed to strip fragment from URI", e, uri.toString());
        }
    }

    private StructuredDocument parseContent(ResourceContent res) {
        if (res.contentType() == null || res.contentType().getId().contains("json")) {
            JsonParser parser = new JsonParser();
            return parser.parse(res.content());
        } else {
            return parserService.parseContent(res);
        }
    }

    /// Loads the core JSON Schema meta-schemas from the module's resources
    /// and registers them in the nodeCache using their official URIs.
    private void loadBuiltInMetaSchemas() {
        Properties props = new Properties();
        String propsPath = "/meta-schema/origin.properties";

        try (InputStream is = getClass().getResourceAsStream(propsPath)) {
            if (is == null) return;

            props.load(is);

            for (String fileName : props.stringPropertyNames()) {
                String publicUriStr = props.getProperty(fileName);
                URI publicUri = URI.create(publicUriStr);

                try (InputStream schemaStream = getClass().getResourceAsStream("/meta-schema/" + fileName)) {
                    if (schemaStream != null) {
                        // Read bytes and convert to String for ResourceContent
                        byte[] bytes = schemaStream.readAllBytes();
                        String jsonContent = new String(bytes, StandardCharsets.UTF_8);

                        // Create ResourceContent with NULL type (defaults to JSON)
                        ResourceContent resource = new ResourceContent(jsonContent, null);

                        // Parse into the AST
                        StructuredDocument doc = parseContent(resource);

                        // Register the root node so $ref to the public URI works instantly
                        nodeCache.put(publicUri, doc.getRoot());
                    }
                }
            }
        } catch (IOException e) {
            throw new SchemaException("Failed to initialize built-in meta-schemas", e, propsPath);
        }
    }
}