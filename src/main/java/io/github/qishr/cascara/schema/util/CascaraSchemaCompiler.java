package io.github.qishr.cascara.schema.util;


import java.net.URI;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import io.github.qishr.cascara.common.lang.*;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.schema.CompiledSchema;
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
    private static final String REF = "$ref";
    private static final String ABSOLUTE = "absolute";
    private static final String ADDITIONAL_PROPERTIES = "additionalProperties";
    private static final String ALL_OF = "allOf";
    private static final String ANY_OF = "anyOf";
    private static final String UNEVALUATED_PROPERTIES = "unevaluatedProperties";
    private static final String DEFAULT = "default";
    private static final String DEFINITIONS = "definitions";
    private static final String DEFINITIONS_INTERNAL = "#/definitions/";
    private static final String DESCRIPTION = "description";
    private static final String ENUM = "enum";
    private static final String EXTENSIONS = "extensions";
    private static final String FORMAT = "format";
    private static final String FRAGMENT = "fragment";
    private static final String ITEM = "item";
    private static final String ITEMS = "items";
    private static final String MINIMUM = "minimum";
    private static final String MINITEMS = "minItems";
    private static final String MAXIMUM = "maximum";
    private static final String MAXITEMS = "maxItems";
    private static final String NAME = "name";
    private static final String ONE_OF = "oneOf";
    private static final String PROPERTIES = "properties";
    private static final String READONLY = "readOnly";
    private static final String REQUIRED = "required";
    private static final String ROOT = "root";
    private static final String TITLE = "title";
    private static final String TYPE = "type";

    private final SchemaResolver resolver;

    @Deprecated
    public CascaraSchemaCompiler(SchemaResolver resolver, boolean resolveRefs) {
        this.resolver = resolver;
    }

    public CascaraSchemaCompiler(SchemaResolver resolver) {
        this.resolver = resolver;
    }

    @Override
    public CompiledSchema compile(StructuredDocument doc) {
        // TODO: This is horrible - get rid of this Optional and just return the URI from getSchemaUri
        return compile(doc, doc.getSchemaUri());
    }

    @Override
    public CompiledSchema compile(StructuredDocument doc, URI originUri) {
        AstNode root = doc.getRoot();

        if (!(root instanceof MapAstNode map)) {
            return null;
        }

        if (originUri == null) {
            AstNode idNode = map.get("$id");
            if (!(idNode instanceof ScalarAstNode scalarId)) return null;
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

        SchemaNode schemaRoot = processNode(map, name, originUri, null);

        CompiledSchema compiled = new CompiledSchema(name, (ObjectSchemaNode) schemaRoot);
        resolver.registerSchema(originUri, compiled);
        return compiled;
    }

    @Override
    public SchemaNode compileSubSchema(MapAstNode node, URI originUri) {
        SchemaNode rootContext = null;

        // Check if we can safely ask the resolver without recursing.
        // If we're already in the middle of a resolve/compile for this URI,
        // we should skip the resolver lookup.
        try {
            // Only ask the resolver if we aren't currently "inside"
            // a resolution for this specific originUri.
            CompiledSchema existing = resolver.getSchema(originUri);
            if (existing != null) {
                rootContext = existing.getRoot();
            }
        } catch (Exception ignored) {}

        if (rootContext == null) {
            // Fallback to a virtual root to prevent the NULL ROOT crash
            // but avoid triggering a new compilation.
            rootContext = new ObjectSchemaNode("fragment-root");
            ((BaseSchemaNode) rootContext).setOriginUri(originUri);
        }

        String name = node.getString(NAME);
        if (name == null) name = FRAGMENT;

        return processNode(node, name, originUri, rootContext);
    }

    @SuppressWarnings({ "unchecked" })
    private SchemaNode processNode(MapAstNode astNode, String name, URI originUri, SchemaNode rootSchema) {
        String refValue = astNode.getString(REF);
        BaseSchemaNode schemaNode;

        if (refValue != null && !refValue.isEmpty()) {
            // Unified: Every pointer is a LazySchemaNode
            schemaNode = new LazySchemaNode(refValue, resolver, rootSchema, originUri);
        } else {
            // Standard Factory
            SchemaType type = extractType(astNode);
            schemaNode = switch (type) {
                case OBJECT -> new ObjectSchemaNode(name);
                case ARRAY  -> new ArraySchemaNode(name);
                // REF type is deleted from the enum; $ref is structural, not a type.
                default     -> new ScalarSchemaNode(name, type);
            };
        }

        // --- Uniform Metadata & Extensions ---
        schemaNode.setOriginAst(astNode);
        schemaNode.setOriginUri(originUri);
        schemaNode.setTitle(astNode.getString(TITLE));
        schemaNode.setDescription(astNode.getString(DESCRIPTION));

        // Capture ALL extension keywords (x-load, x-storage, x-cascade, etc.)
        astNode.getEntries().forEach((entry) -> {
            if (entry instanceof MapEntryAstNode node) {
                AstNode keyBase = node.getKey();
                if (keyBase instanceof ScalarAstNode keyNode) {
                    String key = keyNode.getString();
                    if (key.startsWith("x-")) {
                        AstNode valBase = node.getValue();

                        if (valBase instanceof ScalarAstNode valNode) {
                            // Handle simple hints (x-tracked: true)
                            schemaNode.setCustomHint(key, valNode.getPrimitiveValue());
                        } else if (valBase instanceof MapAstNode mapNode) {
                            // Handle complex hints (x-indexed: { name: "...", unique: true })
                            schemaNode.setCustomHint(key, convertToMap(mapNode));
                        }
                    }
                }
            }
        });

        SchemaNode effectiveRoot = (rootSchema == null) ? schemaNode : rootSchema;

        // ... Compositions, Definitions, and Properties logic remains the same ...

        processComposition(astNode, ALL_OF, schemaNode, originUri, effectiveRoot);
        processComposition(astNode, ANY_OF, schemaNode, originUri, effectiveRoot);
        processComposition(astNode, ONE_OF, schemaNode, originUri, effectiveRoot);

        // Handle Internal Definitions
        if (astNode.get(DEFINITIONS) instanceof MapAstNode defs) {
            defs.getEntries().forEach((entry) -> {
                if (entry instanceof MapEntryAstNode entryNode &&
                        entryNode.getValue() instanceof MapAstNode m &&
                        entryNode.getKey() instanceof ScalarAstNode scalar) {
                    String key = scalar.getString();
                    SchemaNode defNode = processNode(m, key, originUri, effectiveRoot);
                    if (effectiveRoot instanceof ObjectSchemaNode objRoot) {
                        objRoot.addDefinition(key, defNode);
                    }
                }
            });
        }

        // 2. Structural Logic

        if (schemaNode instanceof ObjectSchemaNode objNode) {
            // Handle additionalProperties
            AstNode addProps = astNode.get(ADDITIONAL_PROPERTIES);
            if (addProps instanceof ScalarAstNode scalar && scalar.getPrimitiveValue() instanceof Boolean b) {
                objNode.setAdditionalPropertiesAllowed(b);
            }

            // Handle unevaluatedProperties
            AstNode unevProps = astNode.get(UNEVALUATED_PROPERTIES);
            if (unevProps instanceof ScalarAstNode scalar && scalar.getPrimitiveValue() instanceof Boolean b) {
                objNode.setUnevaluatedPropertiesAllowed(b);
            }

            // TODO: Handle the case where additionalProperties is a sub-schema

            if (astNode.get(PROPERTIES) instanceof MapAstNode props) {
                props.getEntries().forEach((entry) -> {
                    if (entry instanceof MapEntryAstNode entryNode &&
                            entryNode.getKey() instanceof ScalarAstNode scalar &&
                            entryNode.getValue() instanceof MapAstNode m) {
                        String propName = scalar.getString();
                        objNode.addProperty(propName, processNode(m, propName, originUri, effectiveRoot));
                    }
                });
            }
        }
        else if (schemaNode instanceof ArraySchemaNode arrNode) {
            AstNode itemsAst = astNode.get(ITEMS);
            if (itemsAst instanceof MapAstNode itemsMap) {
                arrNode.setItemTemplate(processNode(itemsMap, ITEM, originUri, effectiveRoot));
            }
        }

        attachRules(astNode, schemaNode);
        return schemaNode;
    }

    @SuppressWarnings("unchecked")
    private void processComposition(MapAstNode astNode, String key, BaseSchemaNode parent, URI uri, SchemaNode root) {
        if (astNode.get(key) instanceof SequenceAstNode seq) {
            seq.getElements().forEach(element -> {
                if (element instanceof MapAstNode m) {
                    SchemaNode subSchema = processNode(m, key + "-item", uri, root);

                    if (key.equals(ALL_OF)) {
                        parent.addAllOf(subSchema);
                        // If we are building an object, pull in the inherited properties
                        if (parent instanceof ObjectSchemaNode targetObj) {
                            flattenInheritedProperties(targetObj, subSchema);
                        }
                    }
                    // oneOf/anyOf can be stored for validation, but allOf is our "extends"
                }
            });
        }
    }

    /// Helper to convert AST maps to standard Java maps for the SchemaNode
    @SuppressWarnings("unchecked")
    private Map<String, Object> convertToMap(MapAstNode mapNode) {
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

    private void flattenInheritedProperties(ObjectSchemaNode target, SchemaNode source) {
        // We resolve the lazy node to get the actual properties
        SchemaNode actualSource = (source instanceof LazySchemaNode lazy) ? lazy.getResolved() : source;

        if (actualSource instanceof ObjectSchemaNode sourceObj) {
            sourceObj.getProperties().forEach((propName, propNode) -> {
                // Only add if the child hasn't overridden it locally
                if (!target.getProperties().containsKey(propName)) {
                    target.addProperty(propName, propNode);
                }
            });

            // Follow the chain up (Grandparents)
            actualSource.getAllOf().forEach(grandParent -> flattenInheritedProperties(target, grandParent));
        }
    }

    @SuppressWarnings("unchecked")
    private void attachRules(MapAstNode astNode, BaseSchemaNode schemaNode) {

        AstNode defaultVal = astNode.get(DEFAULT);
        if (defaultVal instanceof ScalarAstNode scalar) {
            schemaNode.setDefaultValue(scalar.getPrimitiveValue());
        }

        if (astNode.get(READONLY) instanceof ScalarAstNode scalar &&
            scalar.getPrimitiveValue() instanceof Boolean b) {
            schemaNode.setReadOnly(b);
        }

        String format = astNode.getString(FORMAT);
        if (format != null && !format.isEmpty()) {
            schemaNode.setFormat(format);
        }

        // Capture absolute preference
        String absolute = astNode.getString(ABSOLUTE);
        if (absolute != null && !absolute.isEmpty()) {
            schemaNode.setFormatOption(ABSOLUTE, absolute);
        }

        // Capture extensions for the FileExtensionRule
        if (astNode.get(EXTENSIONS) instanceof SequenceAstNode extNode) {
            List<String> extensions = extNode.getElements().stream()
                .filter(n -> n instanceof ScalarAstNode)
                .map(n -> ((ScalarAstNode) n).getString())
                .toList();

            // This is what the SE uses to filter the FileChooser
            schemaNode.addRule(new io.github.qishr.cascara.schema.rule.FileExtensionRule(
                extensions.toArray(new String[0])
            ));
        }

        // EnumRule
        if (astNode.get(ENUM) instanceof SequenceAstNode enumNode) {
            List<String> options = enumNode.getElements().stream()
                .filter(n -> n instanceof ScalarAstNode)
                .map(n -> ((ScalarAstNode) n).getString())
                .toList();
            schemaNode.addRule(new EnumRule(options));
        }

        // MinValueRule / MaxValueRule
        double min = astNode.getDouble(MINIMUM, -1);
        if (min != -1) schemaNode.addRule(new MinValueRule(min));

        double max = astNode.getDouble(MAXIMUM, -1);
        if (max != -1) schemaNode.addRule(new MaxValueRule(max));

        // MinItemsRule / MaxItemsRule
        int minItems = astNode.getInteger(MINITEMS, -1);
        if (minItems != -1) schemaNode.addRule(new MinItemsRule(minItems));

        int maxItems = astNode.getInteger(MAXITEMS, -1);
        if (maxItems != -1) schemaNode.addRule(new MaxItemsRule(maxItems));

        // RequiredRule (usually handled at the object level in JSON schema)
        if (astNode.get(REQUIRED) instanceof SequenceAstNode reqNode) {
             List<String> requiredFields = reqNode.getElements().stream()
                .filter(n -> n instanceof ScalarAstNode)
                .map(n -> ((ScalarAstNode) n).getString())
                .toList();
             schemaNode.addRule(new RequiredRule(requiredFields));
        }
    }


    private static SchemaType extractType(MapAstNode node) {
        String typeStr = node.getString(TYPE);
        if (typeStr == null || typeStr.isEmpty()) return SchemaType.OBJECT;
        try {
            return SchemaType.valueOf(typeStr.toUpperCase());
        } catch (IllegalArgumentException e) {
            return SchemaType.OBJECT;
        }
    }
}