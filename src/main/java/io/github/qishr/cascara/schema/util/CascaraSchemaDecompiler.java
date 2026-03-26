package io.github.qishr.cascara.schema.util;

import java.util.HashMap;
import java.util.Map;

import io.github.qishr.cascara.common.lang.ast.QuoteStyle;
import io.github.qishr.cascara.common.lang.simple.SimpleMapEntryNode;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.common.lang.simple.SimpleSequenceNode;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.SchemaException;
import io.github.qishr.cascara.schema.SchemaKeyword;
import io.github.qishr.cascara.schema.ast.ArraySchemaNode;
import io.github.qishr.cascara.schema.ast.LazySchemaNode;
import io.github.qishr.cascara.schema.ast.ObjectSchemaNode;
import io.github.qishr.cascara.schema.ast.SchemaNode;
import io.github.qishr.cascara.schema.rule.EnumRule;
import io.github.qishr.cascara.schema.rule.MaxItemsRule;
import io.github.qishr.cascara.schema.rule.MaxValueRule;
import io.github.qishr.cascara.schema.rule.MinItemsRule;
import io.github.qishr.cascara.schema.rule.MinValueRule;
import io.github.qishr.cascara.schema.rule.RequiredRule;
import io.github.qishr.cascara.schema.rule.ValidationRule;

public final class CascaraSchemaDecompiler {
    private static final String META_SCHEMA = "https://json-schema.org/draft/2020-12/schema";
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

    public SimpleMapNode decompile(CompiledSchema compiled) {
        if (compiled == null || compiled.getRoot() == null) return null;

        SimpleMapNode root = new SimpleMapNode();
        root.put(SCHEMA, scalarValue(META_SCHEMA));
        root.put(ID, scalarValue(compiled.getOriginUri()));

        ObjectSchemaNode compiledRoot = compiled.getRoot();
        if (compiledRoot.getName() != null && !compiledRoot.getName().isEmpty()) {
            root.put("name", scalarValue(compiledRoot.getName()));
        }

        SimpleMapNode decompiled = decompileInternal(compiledRoot);
        for (SimpleMapEntryNode entry : decompiled.getEntries()) {
            root.put(entry.getKey(), entry.getValue());
        }
        return root;
    }

    private SimpleMapNode decompileInternal(SchemaNode compiled) throws SchemaException {
        SimpleMapNode decompiled = new SimpleMapNode();

        if (compiled.getContentMediaType() instanceof String mediaType) {
            decompiled.put(SchemaKeyword.CONTENT_MEDIA_TYPE.string(), scalarValue(mediaType));
        }

        for (var e : standardKeywords(compiled).entrySet()) {
            decompiled.put(e.getKey(), scalarValue(e.getValue()));
        }

        // TODO: There are extensions with Object values...
        for (var e : extensions(compiled).entrySet()) {
            decompiled.put(e.getKey(), scalarValue(e.getValue()));
        }

        // type-specific structure
        SimpleMapNode node = switch (compiled.getType()) {
            case OBJECT  -> {
                if (compiled instanceof ObjectSchemaNode o) {
                    yield object(o);
                }
                else if (compiled instanceof LazySchemaNode bridge) {
                    SimpleMapNode map = new SimpleMapNode();
                    map.put(REF, scalarValue(bridge.getRef()));
                    yield map;
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

        // if (object.getName() != null && !object.getName().isEmpty()) {
        //     map.put("name", scalarValue(object.getName()));
        // }

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

        if (!object.areAdditionalPropertiesAllowed()) {
            map.put("additionalProperties", new SimpleScalarNode(false));
        }
        if (!object.areUnevaluatedPropertiesAllowed()) {
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
                throw new SchemaException("Missing $ref: ", array.getName());
            }
            items.put(REF, scalarValue(lazy.getRef()));
        }
        else {
            items.put(TYPE, scalarValue(template.getType().toString().toLowerCase()));
        }

        map.put(ITEMS, items);
        return map;
    }

    private SimpleMapNode scalar(SchemaNode node) {
        SimpleMapNode map = new SimpleMapNode();
        String type = node.getType().toString().toLowerCase();
        map.put(TYPE, scalarValue(type));
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
        return map;
    }

    private Map<String, Object> extensions(SchemaNode compiled) {
        Map<String, Object> map = new HashMap<>();
        compiled.getExtensions().forEach((key,value) -> {
            map.put(key, value);
        });
        return map;
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
