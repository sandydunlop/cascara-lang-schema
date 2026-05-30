package io.github.qishr.cascara.schema.util;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import io.github.qishr.cascara.common.lang.QuoteStyle;
import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleMapEntryNode;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.common.lang.simple.SimpleSequenceNode;
import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.SchemaException;
import io.github.qishr.cascara.schema.SchemaKeyword;
import io.github.qishr.cascara.schema.SchemaType;
import io.github.qishr.cascara.schema.rule.EnumRule;
import io.github.qishr.cascara.schema.rule.MaxItemsRule;
import io.github.qishr.cascara.schema.rule.MaxValueRule;
import io.github.qishr.cascara.schema.rule.MinItemsRule;
import io.github.qishr.cascara.schema.rule.MinValueRule;
import io.github.qishr.cascara.schema.rule.RequiredRule;
import io.github.qishr.cascara.schema.rule.ValidationRule;
import io.github.qishr.cascara.schema.structure.ArraySchemaNode;
import io.github.qishr.cascara.schema.structure.LazySchemaNode;
import io.github.qishr.cascara.schema.structure.ObjectSchemaNode;
import io.github.qishr.cascara.schema.structure.SchemaNode;
import io.github.qishr.cascara.schema.util.SchemaDecompiler;

public final class SchemaDecompiler {
    private static final String META_SCHEMA_URI = "https://json-schema.org/draft/2020-12/schema";
    private static final String ARRAY = "array";
    private static final String OBJECT = "object";

    // Standard JSON Schema Keywords
    private static final String DEFS = "$defs";
    private static final String ID = "$id";
    private static final String REF = "$ref";
    private static final String SCHEMA = "$schema";
    private static final String DEFAULT = "default";
    private static final String DESCRIPTION = "description";
    private static final String ITEMS = "items";
    private static final String PROPERTIES = "properties";
    private static final String READONLY = "readOnly";
    private static final String TITLE = "title";
    private static final String TYPE = "type";

    private static final String DYNAMIC_ANCHOR = "$dynamicAnchor";
    private static final String DYNAMIC_REF = "$dynamicRef";

    private URI originUri;

    public SimpleDocument decompile(Schema compiled) {
        if (compiled == null || compiled.getRoot() == null) return null;

        SchemaNode compiledRoot = compiled.getRoot();
        SimpleMapNode root = new SimpleMapNode();

        // DYNAMIC DIALECT: Use the Meta-Schema URI actually associated with the node
        // This handles CEMA vs Vanilla automatically.
        URI metaUri = compiledRoot.getMetaSchema().getOriginUri();
        if (metaUri != null) {
            root.put(SCHEMA, scalarValue(metaUri.toString()));
        } else {
            root.put(SCHEMA, scalarValue(META_SCHEMA_URI));
        }

        originUri = compiled.getOriginUri();
        root.put(ID, scalarValue(originUri));

        SimpleMapNode decompiled = decompileInternal(compiledRoot);
        for (SimpleMapEntryNode entry : decompiled.getEntries()) {
            root.put(entry.getKey(), entry.getValue());
        }
        return new SimpleDocument(root);
    }

    private SimpleMapNode decompileInternal(SchemaNode compiled) throws SchemaException {
        SimpleMapNode decompiled = new SimpleMapNode();

        if (compiled.getContentMediaType() instanceof String mediaType) {
            decompiled.put(SchemaKeyword.CONTENT_MEDIA_TYPE.string(), scalarValue(mediaType));
        }

        for (var e : standardKeywords(compiled).entrySet()) {
            decompiled.put(e.getKey(), scalarValue(e.getValue()));
        }

        compiled.getExtensions().forEach((key, value) -> {
            if (value instanceof Map<?,?> mapValue) {
                decompiled.put(key, convertToSimpleMap(mapValue));
            } else {
                decompiled.put(key, scalarValue(value));
            }
        });

        // type-specific structure
        SimpleMapNode node = switch (compiled.getType()) {
            case OBJECT  -> {
                if (compiled instanceof ObjectSchemaNode o) {
                    yield object(o);
                }
                else if (compiled instanceof LazySchemaNode lazy) {
                    // SimpleMapNode map = new SimpleMapNode();
                    // String reference = bridge.getRef();

                    SimpleMapNode refMap = new SimpleMapNode();
                    String ref = lazy.getRef();
                    String refKey = (ref != null && ref.startsWith("#") && !ref.contains("/")) ? DYNAMIC_REF : REF;
                    refMap.put(refKey, scalarValue(ref));
                    yield refMap;
                }
                else {
                    yield null;
                }
            }
            case ARRAY   -> array((ArraySchemaNode) compiled);
            case STRING, BOOLEAN, INTEGER, NUMBER -> scalar(compiled);
            default      -> null;
        };

        if (node != null) {
            for (SimpleMapEntryNode entry : node.getEntries()) {
                decompiled.put(entry.getKey(), entry.getValue());
            }
        }

        // now apply rules as JSON Schema keywords
        applyRules(compiled, decompiled);

        return decompiled;
    }

    private SimpleMapNode object(ObjectSchemaNode object) throws SchemaException {
        SimpleMapNode map = new SimpleMapNode();
        map.put(TYPE, scalarValue(OBJECT));

        // definitions
        if (!object.getDefinitions().isEmpty()) {
            SimpleMapNode definitions = new SimpleMapNode();
            for (var e : object.getDefinitions().entrySet()) {
                definitions.put(e.getKey(), decompileInternal(e.getValue()));
            }
            map.put(DEFS, definitions);
        }

        // properties
        if (!object.getProperties().isEmpty()) {
            SimpleMapNode properties = new SimpleMapNode();
            for (var e : object.getProperties().entrySet()) {
                properties.put(e.getKey(), decompileInternal(e.getValue()));
            }
            map.put(PROPERTIES, properties);
        }

        if (object.getAdditionalPropertiesSchema() != null) {
            map.put("additionalProperties", decompileInternal(object.getAdditionalPropertiesSchema()));
        } else if (!object.areAdditionalPropertiesAllowed()) {
            map.put("additionalProperties", new SimpleScalarNode(false));
        }

        // Handle unevaluatedProperties
        if (object.getUnevaluatedPropertiesSchema() != null) {
            map.put("unevaluatedProperties", decompileInternal(object.getUnevaluatedPropertiesSchema()));
        } else if (!object.areUnevaluatedPropertiesAllowed()) {
            map.put("unevaluatedProperties", new SimpleScalarNode(false));
        }

        return map;
    }

    private SimpleMapNode array(ArraySchemaNode array) {
        SimpleMapNode map = new SimpleMapNode();
        SimpleMapNode items = new SimpleMapNode();
        map.put(TYPE, scalarValue(ARRAY));

        SchemaNode template = array.getItemSchema();
        if (template instanceof LazySchemaNode lazy) {
            if (lazy.getRef() == null || lazy.getRef().isEmpty()) {
                throw new SchemaException("Missing $ref: ", array.getOriginUri().toString(), originUri);
            }
            items.put(REF, scalarValue(lazy.getRef()));
        }
        else {
            items.put(TYPE, scalarValue(template.getType().toString().toLowerCase()));
        }

        if (template != null) {
            // Recurse so nested structures in arrays are fully decompiled
            map.put(ITEMS, decompileInternal(template));
        }

        return map;
    }

    private SimpleMapNode scalar(SchemaNode node) {
        SimpleMapNode map = new SimpleMapNode();
        String type = node.getType().toString().toLowerCase();
        if (type != null && node.getType() != SchemaType.ANY) {
            map.put(TYPE, scalarValue(type));
        }
        return map;
    }

    private SimpleScalarNode scalarValue(Object value) {
        SimpleScalarNode scalar = new SimpleScalarNode(value);
        if (value instanceof String) {
            scalar.setQuoteStyle(QuoteStyle.DOUBLE);
        }
        return scalar;
    }

    private Map<String, Object> standardKeywords(SchemaNode compiled) {
        Map<String, Object> map = new HashMap<>();

        if (compiled.getTitle() != null && !compiled.getTitle().isEmpty()) {
            map.put(TITLE, compiled.getTitle());
        }
        if (compiled.getDescription() != null && !compiled.getDescription().isEmpty()) {
            map.put(DESCRIPTION, compiled.getDescription());
        }
        if (compiled.isReadOnly()) {
            map.put(READONLY, true);
        }
        if (compiled.getDefaultValue() != null) {
            map.put(DEFAULT, compiled.getDefaultValue());
        }
        if (compiled.getDynamicAnchor() != null && !compiled.getDynamicAnchor().isEmpty()) {
            map.put(DYNAMIC_ANCHOR, compiled.getDynamicAnchor());
        }
        return map;
    }

    private SimpleMapNode convertToSimpleMap(Map<?, ?> map) {
        SimpleMapNode node = new SimpleMapNode();
        for (Map.Entry<?, ?> entry : map.entrySet()) {
            Object val = entry.getValue();
            if (val instanceof Map<?, ?> subMap) {
                node.put(entry.getKey().toString(), convertToSimpleMap(subMap));
            } else {
                node.put(entry.getKey().toString(), scalarValue(val));
            }
        }
        return node;
    }

    private SimpleSequenceNode sequenceOf(Iterable<?> values) {
        SimpleSequenceNode seq = new SimpleSequenceNode();
        for (Object v : values) {
            seq.add(scalarValue(v));
        }
        return seq;
    }

    private void applyRules(SchemaNode node, SimpleMapNode target) {
        for (ValidationRule r : node.getRules()) {
            if (r instanceof EnumRule er) {
                target.put("enum", sequenceOf(er.getAllowedValues()));
            } else if (r instanceof MinValueRule min) {
                target.put("minimum", scalarValue(min.getMin()));
            } else if (r instanceof MaxValueRule max) {
                target.put("maximum", scalarValue(max.getMax()));
            } else if (r instanceof MinItemsRule minItems) {
                target.put("minItems", scalarValue(minItems.getMinItems()));
            } else if (r instanceof MaxItemsRule maxItems) {
                target.put("maxItems", scalarValue(maxItems.getMaxItems()));
            } else if (r instanceof RequiredRule req) {
                target.put("required", sequenceOf(req.getRequiredKeys()));
            }
        }
    }
}
