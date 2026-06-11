package io.github.qishr.cascara.schema.util;


import java.net.URI;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.diagnostic.StandardReporter;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.SchemaDiagnosticCode;
import io.github.qishr.cascara.schema.SchemaKeyword;
import io.github.qishr.cascara.schema.SchemaType;
import io.github.qishr.cascara.schema.internal.CompiledSchema;
import io.github.qishr.cascara.schema.rule.EnumRule;
import io.github.qishr.cascara.schema.rule.MaxItemsRule;
import io.github.qishr.cascara.schema.rule.MaxLengthRule;
import io.github.qishr.cascara.schema.rule.MaxValueRule;
import io.github.qishr.cascara.schema.rule.MinItemsRule;
import io.github.qishr.cascara.schema.rule.MinLengthRule;
import io.github.qishr.cascara.schema.rule.MinValueRule;
import io.github.qishr.cascara.schema.rule.RequiredRule;
import io.github.qishr.cascara.schema.structure.ArraySchemaNode;
import io.github.qishr.cascara.schema.structure.BaseSchemaNode;
import io.github.qishr.cascara.schema.structure.LazySchemaNode;
import io.github.qishr.cascara.schema.structure.ObjectSchemaNode;
import io.github.qishr.cascara.schema.structure.ScalarSchemaNode;
import io.github.qishr.cascara.schema.structure.SchemaNode;
import io.github.qishr.cascara.schema.util.SchemaCompiler;

public class SchemaCompiler {
    public static final String META_SCHEMA_URI = "https://json-schema.org/draft/2020-12/schema";

    // TODO: These should be in a TypeAnalyzer
    private static final String ABSOLUTE = "absolute";
    private static final String NAME = "name";

    // Default names for things
    private static final String ROOT = "root";
    private static final String ITEM = "item";

    private SchemaResolver resolver;
    private Reporter reporter = new StandardReporter();

    @Deprecated
    public SchemaCompiler(SchemaResolver resolver, boolean resolveRefs) {
        this.resolver = resolver;
    }

    public SchemaCompiler(SchemaResolver resolver) {
        this.resolver = resolver;
    }

    public SchemaCompiler() {

    }

    public SchemaCompiler setReporter(Reporter reporter) {
        if (reporter == null) {
            this.reporter.error(GenericDiagnosticCode.ERROR, "Reporter must not be null");
        } else {
            this.reporter = reporter;
        }
        return this;
    }

    public SchemaCompiler setResolver(SchemaResolver resolver) {
        this.resolver = resolver;
        return this;
    }

    public Schema compile(AstNode root) {
        return compile(root, null);
    }

    public Schema compile(AstNode root, URI originUri) {
        if (!(root instanceof MapAstNode map)) {
            reporter.error(SchemaDiagnosticCode.ROOT_MUST_BE_MAP);
            return null;
        }

        if (originUri == null) {
            AstNode idNode = map.get(SchemaKeyword.ID.asString());
            if (!(idNode instanceof ScalarAstNode scalarId)) {
                reporter.error(SchemaDiagnosticCode.NO_ID);
                return null;
            }
            originUri = URI.create(scalarId.asString());
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

        // RESOLVE THE META-SCHEMA
        // Look for $schema in the root map. If not found, use a default from the resolver.
        URI metaUri = URI.create(META_SCHEMA_URI);
        String schemaRef = map.getString(SchemaKeyword.SCHEMA.asString());
        if (schemaRef != null) {
            metaUri = originUri.resolve(schemaRef);
        }

        SchemaNode metaRoot = null;
        if (!originUri.equals(metaUri)) {
            try {
                Schema metaDoc = resolver.getSchema(metaUri);
                metaRoot = metaDoc.getRoot();
            } catch (Exception e) {
                reporter.warn(GenericDiagnosticCode.WARN, "Could not resolve meta-schema " + metaUri + ": " + e.getMessage());
            }
        }

        SchemaNode schemaRoot = processNode(map, name, originUri, originUri, null, metaRoot);

        CompiledSchema compiled = new CompiledSchema(originUri, schemaRoot);
        if (resolver != null) {
            resolver.registerSchema(originUri, compiled);
        }
        finalizeNodes(compiled.getRoot());
        return compiled;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private SchemaNode processNode(MapAstNode astNode, String name, URI originUri, URI logicalBaseUri, SchemaNode rootSchema, SchemaNode meta) {
        // 1. Calculate Logical Base ($id) and handle $anchor
        String idValue = astNode.getString(SchemaKeyword.ID.asString());
        URI currentBase = (idValue != null && !idValue.isEmpty())
                ? logicalBaseUri.resolve(idValue)
                : logicalBaseUri;

        String anchor = astNode.getString(SchemaKeyword.ANCHOR.asString());
        String refValue = astNode.getString(SchemaKeyword.REF.asString());
        String dynamicAnchor = astNode.getString(SchemaKeyword.DYNAMIC_ANCHOR.asString());

        if (refValue == null || refValue.isEmpty()) {
            refValue = astNode.getString(SchemaKeyword.DYNAMIC_REF.asString());
        }

        // 1. Check for a local Meta-Schema override ($schema)
        SchemaNode currentMeta = meta;
        String localSchema = astNode.getString(SchemaKeyword.SCHEMA.asString());
        if (localSchema != null) {
            try {
                // Resolve the new meta-schema URI relative to the current base
                URI metaUri = logicalBaseUri.resolve(localSchema);
                if (originUri.equals(metaUri)) {
                    // If we are the meta-schema, our 'meta' is null (or 'this' logic applies)
                    currentMeta = (rootSchema != null) ? rootSchema : null;
                } else {
                    try {
                        currentMeta = resolver.getSchema(metaUri).getRoot();
                    } catch (Exception e) {
                        reporter.warn(SchemaDiagnosticCode.LOCAL_RESOLUTION_FAILED, localSchema);
                    }
                }
            } catch (Exception e) {
                // Fallback to the parent meta if the local one fails to load
                reporter.warn(SchemaDiagnosticCode.LOCAL_RESOLUTION_FAILED, localSchema);
            }
        }

        // 2. Use 'currentMeta' for the rest of this node and its children
        BaseSchemaNode schemaNode;
        if (refValue != null && !refValue.isEmpty()) {
            DynamicScope scope = (resolver instanceof SchemaResolver r) ? r.getCurrentScope() : null;
            schemaNode = new LazySchemaNode(refValue, resolver, rootSchema, currentBase, astNode, scope, currentMeta);
        } else {
            SchemaType type = extractType(astNode);
            schemaNode = switch (type) {
                case OBJECT -> new ObjectSchemaNode(currentMeta);
                case ARRAY  -> new ArraySchemaNode(currentMeta);
                default     -> new ScalarSchemaNode(type, currentMeta);
            };
        }

        if (idValue != null) {
            resolver.registerSchemaNode(currentBase, schemaNode);
        }
        if (anchor != null && !anchor.isEmpty()) {
            resolver.registerSchemaNode(currentBase.resolve("#" + anchor), schemaNode);
        }

        // Store the dynamic anchor on the node
        if (dynamicAnchor != null && !dynamicAnchor.isEmpty()) {
            schemaNode.setDynamicAnchor(dynamicAnchor);
            resolver.registerSchemaNode(currentBase.resolve("#" + dynamicAnchor), schemaNode);
        }

        // --- Metadata ---
        schemaNode.setOriginAst(astNode);
        schemaNode.setOriginUri(originUri);
        schemaNode.setTitle(astNode.getString(SchemaKeyword.TITLE.asString()));
        schemaNode.setDescription(astNode.getString(SchemaKeyword.DESCRIPTION.asString()));
        schemaNode.setContentMediaType(astNode.getString(SchemaKeyword.CONTENT_MEDIA_TYPE.asString()));

        SchemaNode effectiveRoot = (rootSchema == null) ? schemaNode : rootSchema;

        if (schemaNode instanceof ObjectSchemaNode objNode) {
            AstNode addProps = astNode.get(SchemaKeyword.ADDITIONAL_PROPERTIES.asString());
            if (addProps instanceof ScalarAstNode scalar) {
                if (scalar.getPrimitive() instanceof Boolean b) {
                    objNode.setAdditionalPropertiesAllowed(b);
                }
            } else if (addProps instanceof MapAstNode mapAddProps) {
                // It's a schema object
                objNode.setAdditionalPropertiesSchema(
                    processNode(mapAddProps, "additionalProperties", originUri, currentBase, effectiveRoot, meta)
                );
            }

            AstNode unevProps = astNode.get(SchemaKeyword.UNEVALUATED_PROPERTIES.asString());
            if (unevProps instanceof ScalarAstNode scalar) {
                if (scalar.getPrimitive() instanceof Boolean b) {
                    objNode.setUnevaluatedPropertiesAllowed(b);
                }
            } else if (unevProps instanceof MapAstNode mapUnevProps) {
                // It's a schema object
                objNode.setUnevaluatedPropertiesSchema(
                    processNode(mapUnevProps, "unevaluatedProperties", originUri, currentBase, effectiveRoot, meta)
                );
            }

            // Properties recursion
            if (astNode.get(SchemaKeyword.PROPERTIES.asString()) instanceof MapAstNode props) {
                props.getEntries().forEach((entry) -> {
                    if (entry instanceof MapEntryAstNode entryNode &&
                            entryNode.getKey() instanceof ScalarAstNode scalar &&
                            entryNode.getValue() instanceof MapAstNode m) {
                        String propName = scalar.asString();
                        objNode.addProperty(propName, processNode(m, propName, originUri, currentBase, effectiveRoot, meta));
                    }
                });
            }
        }
        else if (schemaNode instanceof ArraySchemaNode arrNode) {
            AstNode itemsAst = astNode.get(SchemaKeyword.ITEMS.asString());
            if (itemsAst instanceof MapAstNode itemsMap) {
                arrNode.setItemTemplate(processNode(itemsMap, ITEM, originUri, currentBase, effectiveRoot, meta));
            }
        }

        // --- Definitions Recursion ---
        AstNode defsNode = astNode.get(SchemaKeyword.DEFINITIONS.asString());
        if (defsNode == null) defsNode = astNode.get(SchemaKeyword.DEFS.asString());
        if (defsNode instanceof MapAstNode defs) {
            defs.getEntries().forEach((entry) -> {
                if (entry instanceof MapEntryAstNode entryNode &&
                        entryNode.getValue() instanceof MapAstNode m &&
                        entryNode.getKey() instanceof ScalarAstNode scalar) {
                    String key = scalar.asString();
                    SchemaNode defNode = processNode(m, key, originUri, currentBase, effectiveRoot, meta);
                    if (effectiveRoot instanceof ObjectSchemaNode objRoot) {
                        objRoot.addDefinition(key, defNode);
                    }
                }
            });
        }

        // --- Logic & Extensions ---
        processComposition(astNode, SchemaKeyword.ALL_OF, schemaNode, currentBase, effectiveRoot, meta);
        processComposition(astNode, SchemaKeyword.ANY_OF, schemaNode, currentBase, effectiveRoot, meta);
        processComposition(astNode, SchemaKeyword.ONE_OF, schemaNode, currentBase, effectiveRoot, meta);

        attachRules(astNode, schemaNode);
        handleExtensions(astNode, schemaNode);

        return schemaNode;
    }

    @SuppressWarnings({ "unchecked", "rawtypes" })
    private void processComposition( MapAstNode astNode, SchemaKeyword key, BaseSchemaNode parent, URI uri, SchemaNode root, SchemaNode meta) {
        if (astNode.get(key.asString()) instanceof SequenceAstNode seq) {
            seq.getElements().forEach(element -> {
                if (element instanceof MapAstNode m) {
                    SchemaNode subSchema = processNode(m, key.asString() + "-item", uri, uri, root, meta);

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
                    String key = keyNode.asString();
                    if (!SchemaKeyword.exists(key)) { // it's not a standard JSONSchema keyword
                        AstNode valBase = node.getValue();

                        if (valBase instanceof ScalarAstNode valNode) {
                            // Handle simple extensions (x-tracked: true)
                            schemaNode.setExtension(key, valNode.getPrimitive());
                        } else if (valBase instanceof MapAstNode mapNode) {
                            // Handle object extensions (x-indexed: { name: "...", unique: true })
                            schemaNode.setExtension(key, convertToMap(mapNode));
                        } else if (valBase instanceof SequenceAstNode seqNode) {
                            // Handle array extensions (x-display-columns: [ "title", "date" ])
                            schemaNode.setExtension(key, convertToList(seqNode));
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
                    result.put(kn.asString(), scalar.getPrimitive());
                } else if (vn instanceof MapAstNode nestedMap) {
                    result.put(kn.asString(), convertToMap(nestedMap));
                } else if (vn instanceof SequenceAstNode nestedSeq) {
                    result.put(kn.asString(), convertToList(nestedSeq));
                }
            }
        });
        return result;
    }

    private List<Object> convertToList(SequenceAstNode<?> seqNode) {
        List<Object> result = new ArrayList<>();
        seqNode.getElements().forEach(element -> {
            if (element instanceof ScalarAstNode scalar) {
                result.add(scalar.getPrimitive());
            } else if (element instanceof MapAstNode nestedMap) {
                result.add(convertToMap(nestedMap));
            } else if (element instanceof SequenceAstNode nestedSeq) {
                result.add(convertToList(nestedSeq));
            }
        });
        return result;
    }

    @SuppressWarnings("unchecked")
    private void attachRules(@SuppressWarnings("rawtypes") MapAstNode astNode, BaseSchemaNode schemaNode) {

        AstNode defaultVal = astNode.get(SchemaKeyword.DEFAULT.asString());
        if (defaultVal instanceof ScalarAstNode scalar) {
            schemaNode.setDefaultValue(scalar.getPrimitive());
        }

        if (astNode.get(SchemaKeyword.READ_ONLY.asString()) instanceof ScalarAstNode scalar &&
            scalar.getPrimitive() instanceof Boolean b) {
            schemaNode.setReadOnly(b);
        }

        String format = astNode.getString(SchemaKeyword.FORMAT.asString());
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

        // EnumRule
        if (astNode.get(SchemaKeyword.ENUM.asString()) instanceof SequenceAstNode enumNode) {
            @SuppressWarnings("rawtypes")
            List<String> options = enumNode.getElements().stream()
                .filter(n -> n instanceof ScalarAstNode)
                .map(n -> ((ScalarAstNode) n).asString())
                .toList();
            schemaNode.addRule(new EnumRule(options));
        }

        // MinValueRule / MaxValueRule
        double min = astNode.getDouble(SchemaKeyword.MINIMUM.asString(), -1);
        if (min != -1) schemaNode.addRule(new MinValueRule(min));

        double max = astNode.getDouble(SchemaKeyword.MAXIMUM.asString(), -1);
        if (max != -1) schemaNode.addRule(new MaxValueRule(max));

        // MinItemsRule / MaxItemsRule
        int minItems = astNode.getInteger(SchemaKeyword.MIN_ITEMS.asString(), -1);
        if (minItems != -1) schemaNode.addRule(new MinItemsRule(minItems));

        int maxItems = astNode.getInteger(SchemaKeyword.MAX_ITEMS.asString(), -1);
        if (maxItems != -1) schemaNode.addRule(new MaxItemsRule(maxItems));

        // MinLengthRule / MaxLengthRule
        int minLength = astNode.getInteger(SchemaKeyword.MIN_LENGTH.asString(), -1);
        if (minLength != -1) schemaNode.addRule(new MinLengthRule(minLength));

        int maxLength = astNode.getInteger(SchemaKeyword.MAX_LENGTH.asString(), -1);
        if (maxLength != -1) schemaNode.addRule(new MaxLengthRule(maxLength));

        // RequiredRule (usually handled at the object level in JSON schema)
        if (astNode.get(SchemaKeyword.REQUIRED.asString()) instanceof SequenceAstNode reqNode) {
             @SuppressWarnings("rawtypes")
             List<String> requiredFields = reqNode.getElements().stream()
                .filter(n -> n instanceof ScalarAstNode)
                .map(n -> ((ScalarAstNode) n).asString())
                .toList();
             schemaNode.addRule(new RequiredRule(requiredFields));
        }
    }

    private static SchemaType extractType(@SuppressWarnings("rawtypes") MapAstNode node) {
        String typeStr = node.getString(SchemaKeyword.TYPE.asString());
        if (typeStr != null && !typeStr.isEmpty()) {
            try {
                return SchemaType.valueOf(typeStr.toUpperCase());
            } catch (IllegalArgumentException e) {
                return SchemaType.ANY;
            }
        }

        // Inference: If it has these structural keys, it's effectively an object
        if (node.containsKey("properties") ||
            node.containsKey("definitions") ||
            node.containsKey("$defs") ||
            node.containsKey("allOf") ||
            node.containsKey("additionalProperties")) {
            return SchemaType.OBJECT;
        }

        return SchemaType.ANY;
    }
}
