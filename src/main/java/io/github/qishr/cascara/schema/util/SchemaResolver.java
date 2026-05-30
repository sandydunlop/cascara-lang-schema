package io.github.qishr.cascara.schema.util;

import java.io.IOException;
import java.io.InputStream;
import java.net.URI;
import java.nio.charset.StandardCharsets;
import java.util.Collections;
import java.util.HashMap;
import java.util.IdentityHashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Set;

import io.github.qishr.cascara.common.io.ContentLoader;
import io.github.qishr.cascara.common.io.IOUtils;
import io.github.qishr.cascara.common.content.ResourceContent;
import io.github.qishr.cascara.common.io.UriScheme;
import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.common.lang.processor.Parser;
import io.github.qishr.cascara.common.spi.ParserFactory;
import io.github.qishr.cascara.common.spi.ServiceException;
import io.github.qishr.cascara.lang.json.processor.JsonParser;
import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.SchemaException;
import io.github.qishr.cascara.schema.SchemaKeyword;
import io.github.qishr.cascara.schema.internal.SchemaUtils;
import io.github.qishr.cascara.schema.structure.ArraySchemaNode;
import io.github.qishr.cascara.schema.structure.LazySchemaNode;
import io.github.qishr.cascara.schema.structure.ObjectSchemaNode;
import io.github.qishr.cascara.schema.structure.SchemaNode;
import io.github.qishr.cascara.schema.util.SchemaResolver;
import io.github.qishr.cascara.schema.util.CascaraSchemaUri.Lifecycle;

public class SchemaResolver {
    private ContentLoader contentLoaderService;

    private static final Map<URI,ResourceContent> metaSchemaResources = new HashMap<>();

    private final Map<URI, SchemaNode> schemaNodeCache = new HashMap<>();
    private final Map<URI, Schema> schemaDocCache = new HashMap<>();

    private final ThreadLocal<DynamicScope> currentScope = new ThreadLocal<>();

    private final SchemaStore schemaStore;

    public SchemaResolver() {
        this.contentLoaderService = new SchemaContentLoader();
        this.schemaStore = SchemaStore.instance();
        loadBuiltInMetaSchemas();
    }

    /// Returns the `Schema` indicated by the given `URI`.
    /// If the `Schema` is cached, it will be retrieved from the cache,
    /// otherwise it will be compiled and returned.
    public Schema getSchema(URI uri) throws SchemaException {
        Schema schema = schemaDocCache.get(uri);
        if (schema != null) return schema;

        ResourceContent content = null;
        if (UriScheme.of(uri) == UriScheme.CASCARA) {
            CascaraSchemaUri schemaUri = CascaraSchemaUri.of(uri);
            if (schemaUri.getLifecycle() != Lifecycle.DYNAMIC) {
                content = schemaStore.get(schemaUri);
            }
        }

        if (content == null) {
            try {
                content = contentLoaderService.getContent(uri);
            } catch (Exception e) {
                throw new SchemaException(e.getMessage(), e, uri);
            }
        }

        StructuredDocument doc = parseContent(content);
        SchemaCompiler compiler = new SchemaCompiler(this);
        return compiler.compile(doc, uri);
    }

    public SchemaNode resolve(String ref, SchemaNode relativeTo) throws SchemaException {
        // Start with an empty root scope for a fresh resolution
        DynamicScope scope = new DynamicScope(null);
        currentScope.set(scope);
        try {
            return resolve(ref, relativeTo, scope);
        } finally {
            currentScope.remove(); // Prevent memory leaks
        }
    }

    public Schema getSchemaForClass(Class<?> clazz) {
        return getSchemaForClass(clazz, null);
    }

    public Schema getSchemaForClass(Class<?> clazz, List<TypeAnalyzer> typeAnalyzers) {
        URI uri = new CascaraSchemaUri(clazz).toUri();
        Schema schema = schemaDocCache.get(uri);
        if (schema == null) {
            // This calls the compiler which adds the compiled schema to the cache
            return generateSchemaForClass(clazz, typeAnalyzers);
        }
        return schema;
    }

    //
    // Cache
    //

    public void registerSchema(URI uri, Schema compiled) {
        schemaDocCache.put(uri, compiled);
        if (UriScheme.of(uri) == UriScheme.CASCARA) {
            CascaraSchemaUri schemaUri = CascaraSchemaUri.of(uri);
            if (schemaUri.getLifecycle() != Lifecycle.DYNAMIC) {
                SchemaStore.instance().put(schemaUri, compiled);
            }
        }
    }

    public void registerSchemaNode(URI uri, SchemaNode node) {
        schemaNodeCache.put(uri, node);
    }

    public Map<URI, Schema> getCachedSchemas() {
        return schemaDocCache;
    }

    //
    // DynamicScope
    // TODO:
    // This feels problematic if it were to be used from two places at the same time.
    //

    public SchemaNode resolve(String ref, SchemaNode relativeTo, DynamicScope scope) throws SchemaException {
        // Set the ThreadLocal so the Compiler can find it during this resolution
        DynamicScope previous = currentScope.get();
        currentScope.set(scope);

        try {
            return resolveInternal(ref, relativeTo, scope);
        } finally {
            // Restore previous scope (handles nested resolutions)
            if (previous != null) {
                currentScope.set(previous);
            } else {
                currentScope.remove();
            }
        }
    }

    public DynamicScope getCurrentScope() { return currentScope.get(); }

    //
    // Private MEthods
    //

    private Schema generateSchemaForClass(Class<?> clazz, List<TypeAnalyzer> typeAnalyzers) throws SchemaException {
        URI originUri = new CascaraSchemaUri(clazz).toUri();
        SchemaCompiler compiler = new SchemaCompiler(this);
        SchemaGenerator generator = new SchemaGenerator();
        if (typeAnalyzers != null) {
            for (TypeAnalyzer ta : typeAnalyzers) {
                generator.registerTypeAnalyzer(ta);
            }
        }
        StructuredDocument schemaDoc = generator.generate(clazz);
        Schema schema = compiler.compile(schemaDoc, originUri);
        return schema;
    }

    /// Internal version that carries the scope
    private SchemaNode resolveInternal(String ref, SchemaNode relativeTo, DynamicScope scope) throws SchemaException {
        URI baseUri = relativeTo.getOriginUri();
        URI targetUri;

        // 1. Intercept for $dynamicRef BEFORE standard URI resolution
        if (isDynamic(ref)) {
            String anchorName = extractAnchorName(ref);
            URI dynamicTarget = scope.findAnchor(anchorName);
            if (dynamicTarget != null) {
                targetUri = dynamicTarget;
            } else {
                // Fallback to standard fragment resolution if no anchor found
                targetUri = baseUri.resolve(ref);
            }
        } else {
            targetUri = baseUri.resolve(ref);
        }

        // If the compiled `SchemaNode` is cached, return it
        SchemaNode cached = schemaNodeCache.get(targetUri);
        if (cached != null) return cached;

        // 3. The Compiler/Document Load (Your existing logic)
        URI docUri = stripFragment(targetUri);
        Schema schemaDoc = getSchema(docUri); // This triggers compilation if needed

        // 4. Fragment Resolution
        String fragment = targetUri.getFragment();
        SchemaNode schemaNode = resolveFragment(schemaDoc, fragment);

        if (schemaNode == null) {
            throw new SchemaException("Resolution failed", ref, relativeTo.getStartLine(),
                                      relativeTo.getStartColumn(), baseUri);
        }

        // 5. Update Cache and return
        schemaNodeCache.put(targetUri, schemaNode);
        return schemaNode;
    }

    private SchemaNode resolveFragment(Schema schemaDoc, String fragment) {
        return resolveFragment(schemaDoc, fragment, new DynamicScope(null));
    }

    private SchemaNode updateScope(SchemaNode node, DynamicScope scope) {
        if (node == null) return null;

        // Check for $dynamicAnchor (using the extension map we set in the compiler)
        Object dynamicAnchor = node.getExtension(SchemaKeyword.DYNAMIC_ANCHOR.string());
        if (dynamicAnchor instanceof String anchorName) {
            // Register this node's URI for this anchor name in the current resolution path
            scope.addAnchor(anchorName, node.getOriginUri());
        }
        return node;
    }

    private boolean isDynamic(String ref) {
        return ref != null && ref.startsWith("#") && !ref.contains("/");
    }

    private String extractAnchorName(String ref) {
        return ref.substring(1);
    }

    private SchemaNode resolveFragment(Schema schemaDoc, String fragment, DynamicScope scope) throws SchemaException {
        if (fragment == null || fragment.isEmpty() || fragment.equals("/")) {
            return updateScope(schemaDoc.getRoot(), scope);
        }

        AstNode targetAst = null;

        // 1. Resolve the AST location (Pointer or Anchor)
        if (fragment.startsWith("/")) {
            try {
                // Your existing SchemaUtils or Pointer logic
                targetAst = SchemaUtils.resolveFragment(schemaDoc.getRoot().getOriginAst(), fragment);
            } catch (Exception ignored) {}
        }

        if (targetAst == null) {
            // Your existing anchor lookup
            targetAst = findNodeByAnchor(schemaDoc.getRoot().getOriginAst(), fragment);
        }

        // 2. Map AST back to SchemaNode
        SchemaNode found = findNodeByAst(schemaDoc.getRoot(), targetAst);

        if (found == null) {
            throw new SchemaException("Could not find node for fragment", fragment, schemaDoc.getOriginUri());
        }

        // 3. Update the Dynamic Scope and return
        return updateScope(found, scope);
    }

    private SchemaNode findNodeByAst(SchemaNode root, AstNode targetAst) {
        // We use a set that uses reference equality (==) instead of .equals()
        return findNodeByAst(root, targetAst, 0, Collections.newSetFromMap(new IdentityHashMap<>()));
    }

    private SchemaNode findNodeByAst(SchemaNode root, AstNode targetAst, int depth, Set<SchemaNode> visited) {
        if (root == null || targetAst == null) return null;

        // 1. Check if we've seen THIS specific instance before
        if (visited.contains(root)) {
            return null;
        }
        visited.add(root);

        // 2. Identity Check
        boolean matchesCurrent = root.getOriginAst() == targetAst;
        boolean matchesOriginal = (root instanceof LazySchemaNode lazy) &&
                                lazy.getInitialAst() == targetAst;

        if (matchesCurrent || matchesOriginal) {
            return root;
        }

        // 3. Traversal (Peeking)
        if (root instanceof LazySchemaNode lazy) {
            SchemaNode internal = lazy.peekResolved();
            // If it's already resolved, search inside the resolution
            if (internal != null && internal != root) {
                return findNodeByAst(internal, targetAst, depth + 1, visited);
            }
            return null;
        }

        // 4. Concrete Traversal
        if (root instanceof ObjectSchemaNode obj) {
            // Definitions usually contain the circular targets
            for (SchemaNode def : obj.getDefinitions().values()) {
                SchemaNode found = findNodeByAst(def, targetAst, depth + 1, visited);
                if (found != null) return found;
            }
            for (SchemaNode prop : obj.getProperties().values()) {
                SchemaNode found = findNodeByAst(prop, targetAst, depth + 1, visited);
                if (found != null) return found;
            }
        }

        if (root instanceof ArraySchemaNode arr) {
            // This is where workItem -> sprint -> workItem loop triggers
            return findNodeByAst(arr.getItemSchema(), targetAst, depth + 1, visited);
        }

        return null;
    }

    private AstNode findNodeByAnchor(AstNode root, String anchor) {
        if (root instanceof MapAstNode map) {
            // 1. Check if this specific node is the target
            String id = map.getString(SchemaKeyword.ID.string());
            String nodeAnchor = map.getString(SchemaKeyword.ANCHOR.string());

            String dynAnchor = map.getString(SchemaKeyword.DYNAMIC_ANCHOR.string());

            if (anchor.equals(id) || anchor.equals(nodeAnchor) || anchor.equals(dynAnchor)) {
                return map;
            }

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

    private URI stripFragment(URI uri) {
        try {
            return new URI(uri.getScheme(), uri.getAuthority(), uri.getPath(), uri.getQuery(), null);
        } catch (Exception e) {
            return uri;
        }
    }

    private StructuredDocument parseContent(ResourceContent res) {
        String contentType;
        if (res.contentType() == null) {
            contentType = "application/schema+json";
        } else {
            contentType = res.contentType().toString();
        }
        try {
            Parser<?,?> parser;
            if (contentType.contains("json")) {
                parser = new JsonParser();
            } else {
                parser = new ParserFactory().create(contentType);
                if (parser == null) {
                    throw new IllegalStateException("Failed to find parser for " + contentType);
                }
            }
            return parser.parse(res.content());
        } catch (ServiceException e) {
            throw new IllegalStateException("Failed to load parser for " + contentType + ": " + e.getMessage(), e);
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
            throw new SchemaException("Failed to initialize built-in meta-schemas", e, null);
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

    private class SchemaContentLoader implements ContentLoader {
        @Override
        public ResourceContent getContent(URI uri) throws IOException {
            return IOUtils.getResource(uri);
        }
    }
}