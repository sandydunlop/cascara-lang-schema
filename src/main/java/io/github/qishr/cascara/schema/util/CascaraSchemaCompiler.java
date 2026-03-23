package io.github.qishr.cascara.schema.util;


import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.SimpleReporter;
import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.SchemaKeyword;
import io.github.qishr.cascara.schema.api.SchemaCompiler;
import io.github.qishr.cascara.schema.api.SchemaResolver;
import io.github.qishr.cascara.schema.ast.ArraySchemaNode;
import io.github.qishr.cascara.schema.ast.BaseSchemaNode;
import io.github.qishr.cascara.schema.ast.LazySchemaNode;
import io.github.qishr.cascara.schema.ast.ObjectSchemaNode;
import io.github.qishr.cascara.schema.ast.ScalarSchemaNode;
import io.github.qishr.cascara.schema.ast.SchemaNode;
import io.github.qishr.cascara.schema.ast.SchemaType;
import io.github.qishr.cascara.schema.rule.EnumRule;
import io.github.qishr.cascara.schema.rule.MaxItemsRule;
import io.github.qishr.cascara.schema.rule.MaxValueRule;
import io.github.qishr.cascara.schema.rule.MinItemsRule;
import io.github.qishr.cascara.schema.rule.MinValueRule;
import io.github.qishr.cascara.schema.rule.RequiredRule;
import io.github.qishr.cascara.schema.util.CascaraSchemaCompiler;

public class CascaraSchemaCompiler implements SchemaCompiler {
    // TODO: These should be in a TypeAnalyzer
    private static final String ABSOLUTE = "absolute";
    private static final String EXTENSIONS = "extensions";
    private static final String NAME = "name";

    // Default names for things
    private static final String ROOT = "root";
    private static final String ITEM = "item";

    private final SchemaResolver resolver;
    private Reporter reporter = new SimpleReporter();

    @Deprecated
    public CascaraSchemaCompiler(SchemaResolver resolver, boolean resolveRefs) {
        this.resolver = resolver;
    }

    public CascaraSchemaCompiler(SchemaResolver resolver) {
        this.resolver = resolver;
    }

    public CascaraSchemaCompiler setReporter(Reporter reporter) {
        if (reporter == null) {
            this.reporter.error("Reporter must not be null");
        } else {
            this.reporter = reporter;
        }
        return this;
    }

    @Override
    public CompiledSchema compile(StructuredDocument doc) {
        return compile(doc, doc.getOriginUri());
    }

    @Override
    public CompiledSchema compile(StructuredDocument doc, URI originUri) {
        if (doc == null) {
            reporter.error("Document must not be null");
            return null;
        }
        AstNode root = doc.getRoot();

        if (!(root instanceof MapAstNode map)) {
            reporter.error("Document root must be an AstMapNode");
            return null;
        }

        if (originUri == null) {
            AstNode idNode = map.get("$id");
            if (!(idNode instanceof ScalarAstNode scalarId)) {
                reporter.error("Document must contain $id or origin URI must be given to compiler");
                return null;
            }
            originUri = URI.create(scalarId.getString());
        }

        String name = map.getString(NAME);
        if (name == null || name.isEmpty()) {
            if (originUri != null) {
                String path = originUri.getPath();
                if (path != null && !path.isEmpty()) {
                    int lastSlash = path.lastIndexOf('/');
                    name = (lastSlash != -1) ? path.substring(lastSlash + 1) : path;
                }
            }
        }
        if (name == null || name.isEmpty()) {
            name = ROOT;
        }

        SchemaNode schemaRoot = processNode(map, name, originUri, originUri, null);

        CompiledSchema compiled = new CompiledSchema(name, (ObjectSchemaNode) schemaRoot);
        resolver.registerSchema(originUri, compiled);
        finalizeNodes(compiled.getRoot());
        return compiled;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private SchemaNode processNode(MapAstNode astNode, String name, URI originUri, URI logicalBaseUri, SchemaNode rootSchema) {

        // 1. Calculate Logical Base ($id) and handle $anchor
        String idValue = astNode.getString(SchemaKeyword.ID.string());
        URI currentBase = (idValue != null && !idValue.isEmpty())
                ? logicalBaseUri.resolve(idValue)
                : logicalBaseUri;

        String anchor = astNode.getString(SchemaKeyword.ANCHOR.string());
        String refValue = astNode.getString(SchemaKeyword.REF.string());
        BaseSchemaNode schemaNode;

        if (refValue != null && !refValue.isEmpty()) {
            schemaNode = new LazySchemaNode(refValue, resolver, rootSchema, currentBase);
        } else {
            SchemaType type = extractType(astNode);
            schemaNode = switch (type) {
                case OBJECT -> new ObjectSchemaNode(name);
                case ARRAY  -> new ArraySchemaNode(name);
                default     -> new ScalarSchemaNode(name, type);
            };
        }


        if (idValue != null) {
            resolver.registerSchemaNode(currentBase, schemaNode);
        }
        if (anchor != null && !anchor.isEmpty()) {
            resolver.registerSchemaNode(currentBase.resolve("#" + anchor), schemaNode);
        }



        // --- Metadata ---
        schemaNode.setOriginAst(astNode);
        schemaNode.setOriginUri(originUri);
        schemaNode.setTitle(astNode.getString(SchemaKeyword.TITLE.string()));
        schemaNode.setDescription(astNode.getString(SchemaKeyword.DESCRIPTION.string()));

        SchemaNode effectiveRoot = (rootSchema == null) ? schemaNode : rootSchema;

        // --- RESTORED: Object-Specific Logic ---
        if (schemaNode instanceof ObjectSchemaNode objNode) {
            // additionalProperties
            AstNode addProps = astNode.get(SchemaKeyword.ADDITIONAL_PROPERTIES.string());
            if (addProps instanceof ScalarAstNode scalar) {
                Object val = scalar.getPrimitiveValue();
                if (val instanceof Boolean b) {
                    objNode.setAdditionalPropertiesAllowed(b);
                }
            }

            // unevaluatedProperties
            AstNode unevProps = astNode.get(SchemaKeyword.UNEVALUATED_PROPERTIES.string());
            if (unevProps instanceof ScalarAstNode scalar) {
                Object val = scalar.getPrimitiveValue();
                if (val instanceof Boolean b) {
                    objNode.setUnevaluatedPropertiesAllowed(b);
                }
            }

            // Properties recursion
            if (astNode.get(SchemaKeyword.PROPERTIES.string()) instanceof MapAstNode props) {
                props.getEntries().forEach((entry) -> {
                    if (entry instanceof MapEntryAstNode entryNode &&
                            entryNode.getKey() instanceof ScalarAstNode scalar &&
                            entryNode.getValue() instanceof MapAstNode m) {
                        String propName = scalar.getString();
                        objNode.addProperty(propName, processNode(m, propName, originUri, currentBase, effectiveRoot));
                    }
                });
            }
        }
        else if (schemaNode instanceof ArraySchemaNode arrNode) {
            AstNode itemsAst = astNode.get(SchemaKeyword.ITEMS.string());
            if (itemsAst instanceof MapAstNode itemsMap) {
                arrNode.setItemTemplate(processNode(itemsMap, ITEM, originUri, currentBase, effectiveRoot));
            }
        }

        // --- Definitions Recursion ---
        AstNode defsNode = astNode.get(SchemaKeyword.DEFINITIONS.string());
        if (defsNode == null) defsNode = astNode.get(SchemaKeyword.DEFS.string());
        if (defsNode instanceof MapAstNode defs) {
            defs.getEntries().forEach((entry) -> {
                if (entry instanceof MapEntryAstNode entryNode &&
                        entryNode.getValue() instanceof MapAstNode m &&
                        entryNode.getKey() instanceof ScalarAstNode scalar) {
                    String key = scalar.getString();
                    SchemaNode defNode = processNode(m, key, originUri, currentBase, effectiveRoot);
                    if (effectiveRoot instanceof ObjectSchemaNode objRoot) {
                        objRoot.addDefinition(key, defNode);
                    }
                }
            });
        }

        // --- Logic & Extensions ---
        processComposition(astNode, SchemaKeyword.ALL_OF, schemaNode, currentBase, effectiveRoot);
        processComposition(astNode, SchemaKeyword.ANY_OF, schemaNode, currentBase, effectiveRoot);
        processComposition(astNode, SchemaKeyword.ONE_OF, schemaNode, currentBase, effectiveRoot);

        attachRules(astNode, schemaNode);
        handleExtensions(astNode, schemaNode);

        return schemaNode;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void processComposition( MapAstNode astNode, SchemaKeyword key, BaseSchemaNode parent, URI uri, SchemaNode root) {
        if (astNode.get(key.string()) instanceof SequenceAstNode seq) {
            seq.getElements().forEach(element -> {
                if (element instanceof MapAstNode m) {
                    // TODO: Again, originUri same as node URI?!
                    SchemaNode subSchema = processNode(m, key.string() + "-item", uri, uri, root);

                    // ATTACH IT to the parent
                    if (key == SchemaKeyword.ALL_OF) {
                        parent.addAllOf(subSchema);
                    }
                    // else if (key == SchemaKeyword.ANY_OF) {
                    //     parent.addAnyOf(subSchema);
                    // } else if (key == SchemaKeyword.ONE_OF) {
                    //     parent.addOneOf(subSchema);
                    // }
                    // oneOf/anyOf can be stored for validation, but allOf is our "extends"
                }
            });
        }
    }

    private void flattenInheritedProperties(ObjectSchemaNode target, SchemaNode source) {
        SchemaNode actualSource = source;
        if (source instanceof LazySchemaNode lazy) {
            actualSource = lazy.getResolved();
        }

        if (actualSource instanceof ObjectSchemaNode sourceObj) {
            // IMPORTANT: Ensure the source itself is finalized before we steal its properties.
            // If it was already finalized, this should be a no-op or a quick guard check.
            finalizeNodes(sourceObj);

            sourceObj.getProperties().forEach((propName, propNode) -> {
                if (!target.getProperties().containsKey(propName)) {
                    target.addProperty(propName, propNode);
                }
            });

            // Continue up the chain
            for (SchemaNode grandParent : sourceObj.getAllOf()) {
                flattenInheritedProperties(target, grandParent);
            }
        }
    }

    private void finalizeNodes(SchemaNode node) {
        if (node == null) return;

        if (node instanceof ObjectSchemaNode obj) {
            // Step A: Recurse to children first (Depth-first)
            // This ensures nested objects are ready before the parent uses them.
            obj.getDefinitions().values().forEach(this::finalizeNodes);
            obj.getProperties().values().forEach(this::finalizeNodes);

            // Step B: Flatten Compositions
            for (SchemaNode base : new java.util.ArrayList<>(obj.getAllOf())) {
                flattenInheritedProperties(obj, base);
            }
        }
        else if (node instanceof ArraySchemaNode array) {
            finalizeNodes(array.getItemSchema());
        }
    }

    private void handleExtensions(MapAstNode<?,?> astNode, SchemaNode schemaNode) {
        // Capture ALL extension keywords (x-load, x-storage, x-cascade, etc.)
        astNode.getEntries().forEach((entry) -> {
            if (entry instanceof MapEntryAstNode node) {
                AstNode keyBase = node.getKey();
                if (keyBase instanceof ScalarAstNode keyNode) {
                    String key = keyNode.getString();
                    if (!SchemaKeyword.exists(key)) { // it's not a standard JSONSchema keyword
                        AstNode valBase = node.getValue();

                        if (valBase instanceof ScalarAstNode valNode) {
                            // Handle simple hints (x-tracked: true)
                            schemaNode.setExtension(key, valNode.getPrimitiveValue());
                        } else if (valBase instanceof MapAstNode mapNode) {
                            // Handle complex hints (x-indexed: { name: "...", unique: true })
                            schemaNode.setExtension(key, convertToMap(mapNode));
                        }
                    }
                }
            }
        });
    }

    /// Helper to convert AST maps to standard Java maps for the SchemaNode
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(@SuppressWarnings("rawtypes") MapAstNode mapNode) {
        Map<String, Object> result = new LinkedHashMap<>();
        mapNode.getEntries().forEach(entry -> {
            if (entry instanceof MapEntryAstNode node && node.getKey() instanceof ScalarAstNode kn) {
                AstNode vn = node.getValue();
                if (vn instanceof ScalarAstNode scalar) {
                    result.put(kn.getString(), scalar.getPrimitiveValue());
                } else if (vn instanceof MapAstNode nestedMap) {
                    result.put(kn.getString(), convertToMap(nestedMap));
                }
            }
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private void attachRules(@SuppressWarnings("rawtypes") MapAstNode astNode, BaseSchemaNode schemaNode) {

        AstNode defaultVal = astNode.get(SchemaKeyword.DEFAULT.string());
        if (defaultVal instanceof ScalarAstNode scalar) {
            schemaNode.setDefaultValue(scalar.getPrimitiveValue());
        }

        if (astNode.get(SchemaKeyword.READ_ONLY.string()) instanceof ScalarAstNode scalar &&
            scalar.getPrimitiveValue() instanceof Boolean b) {
            schemaNode.setReadOnly(b);
        }

        String format = astNode.getString(SchemaKeyword.FORMAT.string());
        if (format != null && !format.isEmpty()) {
            schemaNode.setFormat(format);
        }


        //------------------------------
        // TODO: These should be in a TypeAnalyzer

        // Capture absolute preference
        String absolute = astNode.getString(ABSOLUTE);
        if (absolute != null && !absolute.isEmpty()) {
            schemaNode.setFormatOption(ABSOLUTE, absolute);
        }

        // Capture extensions for the FileExtensionRule
        if (astNode.get(EXTENSIONS) instanceof SequenceAstNode extNode) {
            @SuppressWarnings("rawtypes")
            List<String> extensions = extNode.getElements().stream()
                .filter(n -> n instanceof ScalarAstNode)
                .map(n -> ((ScalarAstNode) n).getString())
                .toList();

            // This is what the SE uses to filter the FileChooser
            schemaNode.addRule(new io.github.qishr.cascara.schema.rule.FileExtensionRule(
                extensions.toArray(new String[0])
            ));
        }
        //------------------------------


        // EnumRule
        if (astNode.get(SchemaKeyword.ENUM.string()) instanceof SequenceAstNode enumNode) {
            @SuppressWarnings("rawtypes")
            List<String> options = enumNode.getElements().stream()
                .filter(n -> n instanceof ScalarAstNode)
                .map(n -> ((ScalarAstNode) n).getString())
                .toList();
            schemaNode.addRule(new EnumRule(options));
        }

        // MinValueRule / MaxValueRule
        double min = astNode.getDouble(SchemaKeyword.MINIMUM.string(), -1);
        if (min != -1) schemaNode.addRule(new MinValueRule(min));

        double max = astNode.getDouble(SchemaKeyword.MAXIMUM.string(), -1);
        if (max != -1) schemaNode.addRule(new MaxValueRule(max));

        // MinItemsRule / MaxItemsRule
        int minItems = astNode.getInteger(SchemaKeyword.MIN_ITEMS.string(), -1);
        if (minItems != -1) schemaNode.addRule(new MinItemsRule(minItems));

        int maxItems = astNode.getInteger(SchemaKeyword.MAX_ITEMS.string(), -1);
        if (maxItems != -1) schemaNode.addRule(new MaxItemsRule(maxItems));

        // RequiredRule (usually handled at the object level in JSON schema)
        if (astNode.get(SchemaKeyword.REQUIRED.string()) instanceof SequenceAstNode reqNode) {
             @SuppressWarnings("rawtypes")
             List<String> requiredFields = reqNode.getElements().stream()
                .filter(n -> n instanceof ScalarAstNode)
                .map(n -> ((ScalarAstNode) n).getString())
                .toList();
             schemaNode.addRule(new RequiredRule(requiredFields));
        }
    }


    private static SchemaType extractType(@SuppressWarnings({ "rawtypes" }) MapAstNode node) {
        String typeStr = node.getString(SchemaKeyword.TYPE.string());
        if (typeStr == null || typeStr.isEmpty()) return SchemaType.OBJECT;
        try {
            return SchemaType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SchemaType.OBJECT;
        }
    }
}