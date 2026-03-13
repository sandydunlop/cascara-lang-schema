package io.github.qishr.cascara.lang.schema.util;


import java.net.URI;
import java.util.List;

import io.github.qishr.cascara.common.lang.*;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.ast.MapEntryAstNode;
import io.github.qishr.cascara.common.lang.ast.ScalarAstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.lang.schema.CompiledSchema;
import io.github.qishr.cascara.lang.schema.api.SchemaCompiler;
import io.github.qishr.cascara.lang.schema.api.SchemaResolver;
import io.github.qishr.cascara.lang.schema.ast.ArraySchemaNode;
import io.github.qishr.cascara.lang.schema.ast.BaseSchemaNode;
import io.github.qishr.cascara.lang.schema.ast.LazySchemaNode;
import io.github.qishr.cascara.lang.schema.ast.ObjectSchemaNode;
import io.github.qishr.cascara.lang.schema.ast.ScalarSchemaNode;
import io.github.qishr.cascara.lang.schema.ast.SchemaNode;
import io.github.qishr.cascara.lang.schema.ast.SchemaType;
import io.github.qishr.cascara.lang.schema.rule.EnumRule;
import io.github.qishr.cascara.lang.schema.rule.MaxItemsRule;
import io.github.qishr.cascara.lang.schema.rule.MaxValueRule;
import io.github.qishr.cascara.lang.schema.rule.MinItemsRule;
import io.github.qishr.cascara.lang.schema.rule.MinValueRule;
import io.github.qishr.cascara.lang.schema.rule.RequiredRule;
import io.github.qishr.cascara.lang.schema.util.CascaraSchemaCompiler;

public class CascaraSchemaCompiler implements SchemaCompiler {
    private static final String REF = "$ref";
    private static final String ABSOLUTE = "absolute";
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
    private static final String LAZY = "lazy";
    private static final String MINIMUM = "minimum";
    private static final String MINITEMS = "minItems";
    private static final String MAXIMUM = "maximum";
    private static final String MAXITEMS = "maxItems";
    private static final String NAME = "name";
    private static final String PROPERTIES = "properties";
    private static final String READONLY = "readOnly";
    private static final String REQUIRED = "required";
    private static final String ROOT = "root";
    private static final String TARGET = "target";
    private static final String TITLE = "title";
    private static final String TYPE = "type";
    private static final String X_HIDDEN = "x-hidden";
    private static final String X_LOAD = "x-load";
    private static final String X_PARAMETER = "x-parameter";
    private static final String X_PROVIDER = "x-provider";

    private final SchemaResolver resolver;
    private final boolean resolveRefs;

    public CascaraSchemaCompiler(SchemaResolver resolver, boolean resolveRefs) {
        this.resolver = resolver;
        this.resolveRefs = resolveRefs;
    }

    public CascaraSchemaCompiler(SchemaResolver resolver) {
        this.resolver = resolver;
        this.resolveRefs = true;
    }

    @Override
    public CompiledSchema compile(StructuredDocument doc) {
        // TODO: This is horrible - get rid of this Optional and just return the URI from getSchemaUri
        return compile(doc, doc.getSchemaUri().orElse(null));
    }

    @Override
    public CompiledSchema compile(StructuredDocument doc, URI originUri) {
        AstNode root = doc.getRoot();

        if (!(root instanceof MapAstNode map)) {
            return null;
        }

        if (originUri == null) {
            AstNode idNode = map.get("id");
            if (!(idNode instanceof ScalarAstNode scalarId)) return null;
            originUri = URI.create(scalarId.getString());
        }

        String name = map.getString(NAME);
        if (name == null) name = ROOT;

        SchemaNode schemaRoot = processNode(map, name, originUri, null);
        if (schemaRoot.getUri() == null) {
            System.out.println();
        }

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
        astNode.getEntries().forEach(entry -> {
            // TODO: Ensure x-hidden is being handled correctly
            if (entry instanceof MapEntryAstNode node && node.getKey() instanceof ScalarAstNode keyNode) {
                String key = keyNode.getString();
                if (key.startsWith("x-")) {
                    if (node.getValue() instanceof ScalarAstNode scalarValue) {
                        schemaNode.setCustomHint(key, scalarValue.getRawValue());
                    }
                }
            }
        });

        SchemaNode effectiveRoot = (rootSchema == null) ? schemaNode : rootSchema;

        // ... Compositions, Definitions, and Properties logic remains the same ...
        // Note: use getCustomHint("x-load") in the migration service later!

        processComposition(astNode, "allOf", schemaNode, originUri, effectiveRoot);
        processComposition(astNode, "anyOf", schemaNode, originUri, effectiveRoot);
        processComposition(astNode, "oneOf", schemaNode, originUri, effectiveRoot);

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
        if (schemaNode instanceof ObjectSchemaNode objNode &&
                astNode.get(PROPERTIES) instanceof MapAstNode props) {
            props.getEntries().forEach((entry) -> {
                if (entry instanceof MapEntryAstNode entryNode &&
                        entryNode.getKey() instanceof ScalarAstNode scalar &&
                        entryNode.getValue() instanceof MapAstNode m) {
                    String propName = scalar.getString();
                    objNode.addProperty(propName, processNode(m, propName, originUri, effectiveRoot));
                }
            });
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
                    if (key.equals("allOf")) parent.addAllOf(subSchema);
                    // else if ... anyOf/oneOf
                }
            });
        }
    }

    // private void hydrateBooleanFlags(MapAstNode astNode, BaseSchemaNode schemaNode) {
    //     if (astNode.get(READONLY) instanceof ScalarAstNode scalar &&
    //         scalar.getPrimitiveValue() instanceof Boolean b) {
    //         schemaNode.setReadOnly(b);
    //     }
    //     if (astNode.get(X_HIDDEN) instanceof ScalarAstNode scalar &&
    //         scalar.getPrimitiveValue() instanceof Boolean b) {
    //         schemaNode.setHidden(b);
    //     }
    // }

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
        if (!format.isEmpty()) {
            schemaNode.setFormat(format);
        }

        // Capture absolute preference
        String absolute = astNode.getString(ABSOLUTE);
        if (!absolute.isEmpty()) {
            schemaNode.setFormatOption(ABSOLUTE, absolute);
        }

        // Capture extensions for the FileExtensionRule
        if (astNode.get(EXTENSIONS) instanceof SequenceAstNode extNode) {
            List<String> extensions = extNode.getElements().stream()
                .filter(n -> n instanceof ScalarAstNode)
                .map(n -> ((ScalarAstNode) n).getString())
                .toList();

            // This is what the SE uses to filter the FileChooser
            schemaNode.addRule(new io.github.qishr.cascara.lang.schema.rule.FileExtensionRule(
                extensions.toArray(new String[0])
            ));
        }

        // // Look for the dynamic provider key in the YAML/JSON
        // String providerId = astNode.getString(X_PROVIDER);
        // if (!providerId.isEmpty()) {
        //     schemaNode.setOptionProvider(providerId);
        // }

        // String parameterId = astNode.getString(X_PARAMETER);
        // if (!parameterId.isEmpty()) {
        //     schemaNode.setProviderParameter(parameterId);
        // }

        // if (astNode.get(X_HIDDEN) instanceof ScalarAstNode scalar) {
        //     if (scalar.getPrimitiveValue() instanceof Boolean b) {
        //         schemaNode.setHidden(b);
        //     }
        // }

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
        String typeStr = node.getString(TYPE).toUpperCase();
        if (typeStr.isEmpty()) return SchemaType.OBJECT;
        try {
            return SchemaType.valueOf(typeStr);
        } catch (IllegalArgumentException e) {
            return SchemaType.OBJECT;
        }
    }

    //
    //
    //


}