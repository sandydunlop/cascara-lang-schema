package io.github.qishr.cascara.schema.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import io.github.qishr.cascara.common.content.ContentLoader;
import io.github.qishr.cascara.common.content.ResourceContent;
import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.common.util.UriScheme;
import io.github.qishr.cascara.lang.json.processor.JsonParser;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.SchemaKeyword;
import io.github.qishr.cascara.schema.api.SchemaCompiler;
import io.github.qishr.cascara.schema.api.SchemaParser;
import io.github.qishr.cascara.schema.api.SchemaResolver;
import io.github.qishr.cascara.schema.api.TypeAnalyzer;
import io.github.qishr.cascara.schema.ast.ArraySchemaNode;
import io.github.qishr.cascara.schema.ast.BaseSchemaNode;
import io.github.qishr.cascara.schema.ast.LazySchemaNode;
import io.github.qishr.cascara.schema.ast.ObjectSchemaNode;
import io.github.qishr.cascara.schema.ast.SchemaNode;
import io.github.qishr.cascara.schema.util.CascaraSchemaResolver;

public class CascaraSchemaResolver implements SchemaResolver {
    private static final String SCHEMA_SERVICE_URI = "cascara://core/schema-service/";

    private ContentLoader contentLoaderService;
    private final SchemaParser parserService;
    private final ClassSchemaGenerator generator;

    private static final Map<URI,ResourceContent> metaSchemaResources = new HashMap<>();

    private final Map<URI, SchemaNode> schemaNodeCache = new HashMap<>();
    private final Map<URI, CompiledSchema> schemaDocCache = new HashMap<>();

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
        schemaDocCache.put(uri, compiled);
    }

    @Override
    public void registerSchemaNode(URI uri, SchemaNode node) {
        schemaNodeCache.put(uri, node);
    }

    @Override
    public CompiledSchema getSchemaForClass(Class<?> clazz) throws SchemaException {
        URI originUri = getSchemaUriForClass(clazz);
        CompiledSchema schema = schemaDocCache.get(originUri);
        if (schema == null) {
            schema = generateSchemaForClass(clazz);
            schemaDocCache.put(originUri, schema);
        }
        return schema;
    }

    /// Returns the `CompiledSchema` indicated by the given `URI`.
    /// If the `CompiledSchema` is cached, it will be retrieved from the cache,
    /// otherwise it will be compiled and returned.
    @Override
    public CompiledSchema getSchema(URI uri) throws SchemaException {
        CompiledSchema schema = schemaDocCache.get(uri);
        if (schema != null) return schema;

        // // If it's a cascara URI and it's not in the cache, it's a bug in the
        // // bootstrap/compilation order. Do NOT ask the ContentLoader.
        // if (UriScheme.of(uri) == UriScheme.CASCARA) {
        //     throw new SchemaException(
        //         "Cascara schema not found in resolver cache. " +
        //         "Ensure the schema is registered before resolution.", uri.toString()
        //     );
        // }

        StructuredDocument doc;
        try {
            ResourceContent content = contentLoaderService.getContent(uri);
            doc = parseContent(content);
        } catch (IOException e) {
            throw new SchemaException("Could not load AST for URI", e, uri.toString());
        }

        SchemaCompiler compiler = new CascaraSchemaCompiler(this);
        return compiler.compile(doc, uri);
    }

    @Override
    public Map<URI, CompiledSchema> getCachedSchemas() {
        return schemaDocCache;
    }

    @Override
    public SchemaNode resolve(String ref, SchemaNode relativeTo) throws SchemaException {
        URI baseUri = relativeTo.getOriginUri();
        URI targetUri = baseUri.resolve(ref);
        URI docUri = stripFragment(targetUri);

        // If the compiled `SchemaNode` is cached, return it
        SchemaNode schemaNode = schemaNodeCache.get(targetUri);
        if (schemaNode != null) return schemaNode;

        // Get the `CompiledSchema` document
        CompiledSchema schemaDoc = getSchema(docUri);

        // Resolve the fragment against the schema
        String fragment = targetUri.getFragment();
        schemaNode = resolveFragment(schemaDoc, fragment);

        if (schemaNode == null) {
            throw new SchemaException("Resolution failed",  ref);
        }

        return schemaNode;
    }

    //
    //
    //

    private URI getSchemaUriForClass(Class<?> clazz) {
        String origin = SCHEMA_SERVICE_URI + clazz.getName();
        URI originUri = URI.create(origin);
        return originUri;
    }

    private SchemaNode resolveFragment(CompiledSchema schemaDoc, String fragment) {
        if (fragment == null || fragment.isEmpty()) {
            return schemaDoc.getRoot();
        }

        AstNode targetAst = null;

        // 1. Try resolving as a JSON Pointer (e.g., /definitions/bug)
        if (fragment.startsWith("/")) {
            try {
                targetAst = SchemaUtils.resolveFragment(schemaDoc.getRoot().getOriginAst(), fragment);
            } catch (Exception ignored) {
                // Pointer resolution failed, might be an anchor instead
            }
        }

        // 2. If not a pointer, or pointer failed, treat as a Plain Name Anchor
        if (targetAst == null) {
            targetAst = findNodeByAnchor(schemaDoc.getRoot().getOriginAst(), fragment);
        }

        // 3. Link the found AST back to the compiled SchemaNode tree
        return findNodeByAst(schemaDoc.getRoot(), targetAst);
    }

    private SchemaNode findNodeByAst(SchemaNode root, AstNode targetAst) {
        if (root == null || targetAst == null) return null;

        // 1. If this is the node, check if it's the "Real" one.
        // We only return it immediately if it's NOT lazy.
        if (!(root instanceof LazySchemaNode) &&
            root instanceof BaseSchemaNode base &&
            base.getOriginAst() == targetAst) {
            return root;
        }

        // 2. SEARCH DEFINITIONS FIRST.
        // This is the fix for the 'entities' schema. By looking here first,
        // we find the concrete ObjectSchemaNode before we ever see the Lazy version.
        if (root instanceof ObjectSchemaNode obj) {
            for (SchemaNode def : obj.getDefinitions().values()) {
                SchemaNode found = findNodeByAst(def, targetAst);
                if (found != null) return found;
            }

            // 3. SEARCH PROPERTIES SECOND.
            for (SchemaNode prop : obj.getProperties().values()) {
                SchemaNode found = findNodeByAst(prop, targetAst);
                if (found != null) return found;
            }
        }

        // 4. FALLBACK: If we've searched the whole tree and ONLY found a Lazy node,
        // we might have to return it to avoid a total failure, but ideally,
        // Phase 1 should have created a concrete node for every definition.
        if (root instanceof LazySchemaNode lazy &&
            ((BaseSchemaNode)lazy).getOriginAst() == targetAst) {
            return root;
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

    private CompiledSchema generateSchemaForClass(Class<?> clazz) throws SchemaException {
        URI originUri = getSchemaUriForClass(clazz);
        SchemaCompiler compiler = new CascaraSchemaCompiler(this);
        StructuredDocument schemaDoc = generator.generate(clazz);
        CompiledSchema compiledSchema = compiler.compile(schemaDoc, originUri);
        return compiledSchema;
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
    /// and registers them in the `schemaDocCache` using their official URIs.
    private void loadBuiltInMetaSchemas() {
        // 1. Load and cache the meta-schema content
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
                        byte[] bytes = schemaStream.readAllBytes();
                        String jsonContent = new String(bytes, StandardCharsets.UTF_8);
                        ResourceContent resource = new ResourceContent(jsonContent, null);
                        metaSchemaResources.put(publicUri, resource);
                    }
                }
            }
        } catch (IOException e) {
            throw new SchemaException("Failed to initialize built-in meta-schemas", e, propsPath);
        }

        // 2. Temporarily swap the content loader for one that only loads cached meta schemas
        ContentLoader realLoader = contentLoaderService;
        contentLoaderService = new ContentLoader() {
            @Override
            public ResourceContent getContent(URI uri) throws IOException {
                return metaSchemaResources.get(uri);
            }
        };

        // 3. Compile and cache the meta schemas
        for (URI metaSchemaUri : metaSchemaResources.keySet()) {
            getSchema(metaSchemaUri);
        }

        // 4. Restore the real content loader
        contentLoaderService = realLoader;
    }
}