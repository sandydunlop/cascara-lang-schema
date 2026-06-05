package io.github.qishr.cascara.schema.util;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

import io.github.qishr.cascara.common.lang.QuoteStyle;
import io.github.qishr.cascara.common.lang.reference.ReferenceMapEntryNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceMapNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceScalarNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceSequenceNode;
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

    private URI originUri;

    public ReferenceMapNode decompile(Schema compiled) {
        if (compiled == null || compiled.getRoot() == null) return null;

        SchemaNode compiledRoot = compiled.getRoot();
        ReferenceMapNode root = new ReferenceMapNode();

        // DYNAMIC DIALECT: Use the Meta-Schema URI actually associated with the node
        // This handles CEMA vs Vanilla automatically.
        URI metaUri = compiledRoot.getMetaSchema().getOriginUri();
        if (metaUri != null) {
            root.put(SchemaKeyword.SCHEMA.asString(), scalarValue(metaUri.toString()));
        } else {
            root.put(SchemaKeyword.SCHEMA.asString(), scalarValue(META_SCHEMA_URI));
        }

        originUri = compiled.getOriginUri();
        root.put(SchemaKeyword.ID.asString(), scalarValue(originUri));

        ReferenceMapNode decompiled = decompileInternal(compiledRoot);
        for (ReferenceMapEntryNode entry : decompiled.getEntries()) {
            root.put(entry.getKey(), entry.getValue());
        }
        return root;
    }

    private ReferenceMapNode decompileInternal(SchemaNode compiled) throws SchemaException {
        ReferenceMapNode decompiled = new ReferenceMapNode();

        if (compiled.getContentMediaType() instanceof String mediaType) {
            decompiled.put(SchemaKeyword.CONTENT_MEDIA_TYPE.asString(), scalarValue(mediaType));
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
        ReferenceMapNode node = switch (compiled.getType()) {
            case OBJECT  -> {
                if (compiled instanceof ObjectSchemaNode o) {
                    yield object(o);
                }
                else if (compiled instanceof LazySchemaNode lazy) {
                    // ReferenceMapNode map = new ReferenceMapNode();
                    // String reference = bridge.getRef();

                    ReferenceMapNode refMap = new ReferenceMapNode();
                    String ref = lazy.getRef();
                    String refKey = (ref != null && ref.startsWith("#") && !ref.contains("/")) ? SchemaKeyword.DYNAMIC_REF.asString() : SchemaKeyword.REF.asString();
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
            for (ReferenceMapEntryNode entry : node.getEntries()) {
                decompiled.put(entry.getKey(), entry.getValue());
            }
        }

        // now apply rules as JSON Schema keywords
        applyRules(compiled, decompiled);

        return decompiled;
    }

    private ReferenceMapNode object(ObjectSchemaNode object) throws SchemaException {
        ReferenceMapNode map = new ReferenceMapNode();
        map.put(SchemaKeyword.TYPE.asString(), scalarValue(SchemaType.OBJECT.asString()));

        // definitions
        if (!object.getDefinitions().isEmpty()) {
            ReferenceMapNode definitions = new ReferenceMapNode();
            for (var e : object.getDefinitions().entrySet()) {
                definitions.put(e.getKey(), decompileInternal(e.getValue()));
            }
            map.put(SchemaKeyword.DEFS.asString(), definitions);
        }

        // properties
        if (!object.getProperties().isEmpty()) {
            ReferenceMapNode properties = new ReferenceMapNode();
            for (var e : object.getProperties().entrySet()) {
                properties.put(e.getKey(), decompileInternal(e.getValue()));
            }
            map.put(SchemaKeyword.PROPERTIES.asString(), properties);
        }

        if (object.getAdditionalPropertiesSchema() != null) {
            map.put("additionalProperties", decompileInternal(object.getAdditionalPropertiesSchema()));
        } else if (!object.areAdditionalPropertiesAllowed()) {
            map.put("additionalProperties", new ReferenceScalarNode(false));
        }

        // Handle unevaluatedProperties
        if (object.getUnevaluatedPropertiesSchema() != null) {
            map.put("unevaluatedProperties", decompileInternal(object.getUnevaluatedPropertiesSchema()));
        } else if (!object.areUnevaluatedPropertiesAllowed()) {
            map.put("unevaluatedProperties", new ReferenceScalarNode(false));
        }

        return map;
    }

    private ReferenceMapNode array(ArraySchemaNode array) {
        ReferenceMapNode map = new ReferenceMapNode();
        ReferenceMapNode items = new ReferenceMapNode();
        map.put(SchemaKeyword.TYPE.asString(), scalarValue(SchemaType.ARRAY.asString()));

        SchemaNode template = array.getItemSchema();
        if (template instanceof LazySchemaNode lazy) {
            if (lazy.getRef() == null || lazy.getRef().isEmpty()) {
                throw new SchemaException("Missing $ref: ", array.getOriginUri().toString(), originUri);
            }
            items.put(SchemaKeyword.REF.asString(), scalarValue(lazy.getRef()));
        }
        else {
            items.put(SchemaKeyword.TYPE.asString(), scalarValue(template.getType().toString().toLowerCase()));
        }

        if (template != null) {
            // Recurse so nested structures in arrays are fully decompiled
            map.put(SchemaKeyword.ITEMS.asString(), decompileInternal(template));
        }

        return map;
    }

    private ReferenceMapNode scalar(SchemaNode node) {
        ReferenceMapNode map = new ReferenceMapNode();
        String type = node.getType().toString().toLowerCase();
        if (type != null && node.getType() != SchemaType.ANY) {
            map.put(SchemaKeyword.TYPE.asString(), scalarValue(type));
        }
        return map;
    }

    private ReferenceScalarNode scalarValue(Object value) {
        ReferenceScalarNode scalar = new ReferenceScalarNode(value);
        if (value instanceof String) {
            scalar.setQuoteStyle(QuoteStyle.DOUBLE);
        }
        return scalar;
    }

    private Map<String, Object> standardKeywords(SchemaNode compiled) {
        Map<String, Object> map = new HashMap<>();
        if (compiled.getDefaultValue() != null) {
            map.put(SchemaKeyword.DEFAULT.asString(), compiled.getDefaultValue());
        }
        if (compiled.getDescription() != null && !compiled.getDescription().isEmpty()) {
            map.put(SchemaKeyword.DESCRIPTION.asString(), compiled.getDescription());
        }
        if (compiled.getDynamicAnchor() != null && !compiled.getDynamicAnchor().isEmpty()) {
            map.put(SchemaKeyword.DYNAMIC_ANCHOR.asString(), compiled.getDynamicAnchor());
        }
        if (compiled.getFormat() != null && !compiled.getFormat().isEmpty()) {
            map.put(SchemaKeyword.FORMAT.asString(), compiled.getFormat());
        }
        if (compiled.isReadOnly()) {
            map.put(SchemaKeyword.READ_ONLY.asString(), true);
        }
        if (compiled.getTitle() != null && !compiled.getTitle().isEmpty()) {
            map.put(SchemaKeyword.TITLE.asString(), compiled.getTitle());
        }
        return map;
    }

    private ReferenceMapNode convertToSimpleMap(Map<?, ?> map) {
        ReferenceMapNode node = new ReferenceMapNode();
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

    private ReferenceSequenceNode sequenceOf(Iterable<?> values) {
        ReferenceSequenceNode seq = new ReferenceSequenceNode();
        for (Object v : values) {
            seq.add(scalarValue(v));
        }
        return seq;
    }

    private void applyRules(SchemaNode node, ReferenceMapNode target) {
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
