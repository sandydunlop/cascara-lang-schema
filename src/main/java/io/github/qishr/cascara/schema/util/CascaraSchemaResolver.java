package io.github.qishr.cascara.schema.util;

import java.io.IOException;
import java.net.URI;
import java.util.HashMap;
import java.util.Map;
import java.util.Objects;

import io.github.qishr.cascara.common.content.ContentLoader;
import io.github.qishr.cascara.common.content.ResourceContent;
import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.api.SchemaCompiler;
import io.github.qishr.cascara.schema.api.SchemaParser;
import io.github.qishr.cascara.schema.api.SchemaResolver;
import io.github.qishr.cascara.schema.api.TypeAnalyzer;
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
        CompiledSchema schema = schemaCache.get(uri);
        if (schema == null) {
            try {
                // 1. Load both content AND type in one go
                ResourceContent resource = contentLoaderService.getContent(uri);

                // 2. Parser service now gets the guaranteed ContentType
                StructuredDocument ast = parserService.parseContent(resource);

                // 3. Compile as before
                SchemaCompiler compiler = new CascaraSchemaCompiler(this);
                schema = compiler.compile(ast, uri);
                schemaCache.put(uri, schema);
            } catch (SchemaException | IOException e) {
                throw new SchemaException("Failed to load schema", e, uri.toString());
            }
        }
        return schema;
    }

    @Override
    public Map<URI, CompiledSchema> getCachedSchemas() {
        return schemaCache;
    }

    // @Override
    // public SchemaNode resolve(String ref, SchemaNode relativeTo) throws SchemaException {
    //     try {
    //         URI baseUri = relativeTo.getOriginUri();
    //         URI targetUri = baseUri.resolve(ref);



    //         // -------------------------- New Code
    //         // 1. Instant Lookup: If it's an anchor or a pre-cached document, we're done.
    //         AstNode targetAst = nodeCache.get(targetUri);

    //         if (targetAst == null) {
    //             // 2. Fallback: If it's a JSON Pointer (starts with /), we walk the tree.
    //             String fragment = targetUri.getFragment();
    //             if (fragment != null && fragment.startsWith("/")) {
    //                 // Load the base document first, then walk it
    //                 URI docUri = stripFragment(targetUri);
    //                 AstNode root = getOrLoadAst(docUri);
    //                 targetAst = resolveFragment(fragment, root);
    //             }
    //         }
    //         // --------------------------




    //         // -------------------------- OLD CODE
    //         SchemaCompiler compiler = new CascaraSchemaCompiler(this);
    //         URI docUri;

    //         if (isSameDocument(baseUri, targetUri)) {
    //             // Same document: use the origin AST of the current schema
    //             docUri = baseUri;
    //             AstNode rootAst = relativeTo.getOriginAst();
    //             String fragment = targetUri.getFragment();

    //             targetAst = (fragment != null && !fragment.isEmpty())
    //                 ? resolveFragment("#" + fragment, rootAst)
    //                 : rootAst;
    //         } else {
    //             // Different document: load/parse/cache it
    //             docUri = new URI(
    //                 targetUri.getScheme(),
    //                 targetUri.getAuthority(),
    //                 targetUri.getPath(),
    //                 targetUri.getQuery(),
    //                 null
    //             ).normalize();

    //             // 1. Check if we already have this compiled
    //             CompiledSchema existingSchema = schemaCache.get(docUri);
    //             if (existingSchema != null) {
    //                 String fragment = targetUri.getFragment();
    //                 if (fragment == null || fragment.isEmpty()) {
    //                     // Return the origin AST of the root
    //                     targetAst = existingSchema.getRoot().getOriginAst();
    //                 } else {
    //                     // If there's a fragment, we still need the AST to find the specific part
    //                     targetAst = resolveFragment(fragment, existingSchema.getRoot().getOriginAst());
    //                 }
    //             } else {

    //                 StructuredDocument doc = (StructuredDocument) nodeCache.computeIfAbsent(docUri, uri -> {
    //                     if (contentLoaderService == null) {
    //                         throw new SchemaException("Content loader required for URI", uri.toString());
    //                     }
    //                     try {
    //                         ResourceContent content = contentLoaderService.getContent(uri);
    //                         return parserService.parseContent(content);
    //                     } catch (IOException e) {
    //                         e.printStackTrace();
    //                         return null;
    //                     }
    //                 });
    //                 // CompiledSchema doc = getSchema(docUri);

    //                 if (doc == null) return null;

    //                 String fragment = targetUri.getFragment();
    //                 targetAst = (fragment != null && !fragment.isEmpty())
    //                     ? resolveFragment(fragment, doc)
    //                     : doc;
    //             }
    //         }
    //         // --------------------------




    //         if (targetAst == null) return null;

    //         // Compile whatever we resolved into a SchemaNode
    //         if (targetAst instanceof StructuredDocument structuredDoc) {
    //             CompiledSchema compiledSchema = compiler.compile(structuredDoc, docUri);
    //             return compiledSchema.getRoot();
    //         } else if (targetAst instanceof MapAstNode map) {
    //             return compiler.compileSubSchema(map, docUri);
    //         }




    //         throw new SchemaException("Resolver returned unsupported AST node type: " +
    //                                   targetAst.getClass(), ref);

    //     } catch (Exception e) {
    //         e.printStackTrace();
    //         throw new SchemaException("Resolution failed: " + e.getMessage(), e, ref);
    //     }
    // }

    @Override
    public SchemaNode resolve(String ref, SchemaNode relativeTo) throws SchemaException {
        try {
            URI baseUri = relativeTo.getOriginUri();
            URI targetUri = baseUri.resolve(ref);

            SchemaCompiler compiler = new CascaraSchemaCompiler(this);
            URI docUri = stripFragment(targetUri);

            // 1. Instant Lookup (Anchors or already cached nodes)
            AstNode targetAst = nodeCache.get(targetUri);

            if (targetAst == null) {
                String fragment = targetUri.getFragment();

                // 2. Short-circuit: Is it in the same document we are already looking at?
                // if (isSameDocument(baseUri, targetUri)) {
                //     AstNode rootAst = relativeTo.getOriginAst();
                //     // Ensure fragment exists and is a path, otherwise use root
                //     targetAst = (fragment != null && !fragment.isEmpty())
                //         ? resolveFragment(fragment, rootAst)
                //         : rootAst;
                // }
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
                    // 3. Different document: Now we use the loader
                    if (fragment != null && fragment.startsWith("/")) {
                        AstNode root = getOrLoadAst(docUri);
                        targetAst = resolveFragment(fragment, root);
                    } else {
                        // Plain anchor in external doc
                        getSchema(docUri);
                        targetAst = nodeCache.get(targetUri);
                    }
                }
            }

            if (targetAst == null) {
                throw new SchemaException("Could not resolve reference: " + ref, targetUri.toString());
            }

            // ... rest of compilation logic (unchanged)
            if (targetAst instanceof StructuredDocument structuredDoc) {
                return compiler.compile(structuredDoc, docUri).getRoot();
            } else if (targetAst instanceof MapAstNode map) {
                return compiler.compileSubSchema(map, docUri);
            }

            throw new SchemaException("Unsupported AST type: " + targetAst.getClass(), ref);

        } catch (Exception e) {
            if (e instanceof SchemaException se) throw se;
            throw new SchemaException("Resolution failed: " + e.getMessage(), e, ref);
        }
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
        // Check nodeCache first (which stores both documents and anchors)
        AstNode existing = nodeCache.get(docUri);
        if (existing != null) return existing;

        try {
            ResourceContent content = contentLoaderService.getContent(docUri);
            StructuredDocument doc = parserService.parseContent(content);
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
}