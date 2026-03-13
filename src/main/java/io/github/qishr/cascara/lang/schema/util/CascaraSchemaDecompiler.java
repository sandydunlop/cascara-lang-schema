package io.github.qishr.cascara.lang.schema.util;

import java.util.HashMap;
import java.util.Map;

import io.github.qishr.cascara.common.lang.ast.QuoteStyle;
import io.github.qishr.cascara.common.lang.simple.SimpleMapEntryNode;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.common.lang.simple.SimpleSequenceNode;
import io.github.qishr.cascara.lang.schema.CompiledSchema;
import io.github.qishr.cascara.lang.schema.ast.ArraySchemaNode;
import io.github.qishr.cascara.lang.schema.ast.LazySchemaNode;
import io.github.qishr.cascara.lang.schema.ast.ObjectSchemaNode;
import io.github.qishr.cascara.lang.schema.ast.SchemaNode;
import io.github.qishr.cascara.lang.schema.rule.EnumRule;
import io.github.qishr.cascara.lang.schema.rule.MaxItemsRule;
import io.github.qishr.cascara.lang.schema.rule.MaxValueRule;
import io.github.qishr.cascara.lang.schema.rule.MinItemsRule;
import io.github.qishr.cascara.lang.schema.rule.MinValueRule;
import io.github.qishr.cascara.lang.schema.rule.RequiredRule;
import io.github.qishr.cascara.lang.schema.rule.ValidationRule;

public final class CascaraSchemaDecompiler {
    private static final String META_SCHEMA = "https://json-schema.org/draft/2020-12/schema";
    private static final String ARRAY = "array";
    private static final String OBJECT = "object";

    private static final String TRUE = "true";
    private static final String FALSE = "false";

    // Standard JSON Schema Keywords
    private static final String TITLE = "title";
    private static final String DESCRIPTION = "description";
    private static final String DEFAULT = "default";
    private static final String READONLY = "readOnly";

    // Cascara Schema Extension Keywords
    // private static final String X_HIDDEN = "x-hidden";
    // private static final String X_LOAD = "x-load"; // TODO: Not handled yet
    // private static final String X_PARAMETER = "x-parameter";
    // private static final String X_PROVIDER = "x-provider";

    public SimpleMapNode decompile(CompiledSchema compiled) {
        if (compiled == null) return null;

        SimpleMapNode root = new SimpleMapNode();
        root.put("$schema", scalarValue(META_SCHEMA));
        root.put("$id", scalarValue(compiled.getUri()));
        SimpleMapNode decompiled = decompileInternal(compiled.getRoot());
        for (SimpleMapEntryNode entry : decompiled.getEntries()) {
            root.put(entry.getKey(), entry.getValue());
        }
        return root;
    }

    private SimpleMapNode decompileInternal(SchemaNode compiled) throws SchemaException {
        SimpleMapNode decompiled = new SimpleMapNode();

        // standard + extension keywords
        for (var e : standardKeywords(compiled).entrySet()) {
            decompiled.put(e.getKey(), scalarValue(e.getValue()));
        }
        for (var e : customHints(compiled).entrySet()) {
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
                    map.put("$ref", scalarValue(bridge.getRef()));
                    yield map;
                }
                else {
                    yield null;
                }
            }
            case ARRAY   -> array((ArraySchemaNode) compiled);
            case STRING, BOOLEAN, INTEGER, NUMBER -> scalar(compiled);
            // case REF     -> ref((ReferenceSchemaNode) compiled);
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
        map.put("type", scalarValue(OBJECT));

        // definitions
        if (!object.getDefinitions().isEmpty()) {
            SimpleMapNode definitions = new SimpleMapNode();
            for (var e : object.getDefinitions().entrySet()) {
                definitions.put(e.getKey(), decompileInternal(e.getValue()));
            }
            map.put("definitions", definitions);
        }

        // properties
        if (!object.getProperties().isEmpty()) {
            SimpleMapNode properties = new SimpleMapNode();
            for (var e : object.getProperties().entrySet()) {
                properties.put(e.getKey(), decompileInternal(e.getValue()));
            }
            map.put("properties", properties);
        }

        return map;
    }


    private SimpleMapNode array(ArraySchemaNode array) {
        SimpleMapNode map = new SimpleMapNode();
        SimpleMapNode items = new SimpleMapNode();
        map.put("type", scalarValue(ARRAY));

        SchemaNode template = array.getItemTemplate();
        // if (template instanceof ReferenceSchemaNode ref) {
        //     if (ref.getTargetType() == null || ref.getTargetType().isEmpty()) {
        //         throw new SchemaException("Reference node has no ref: ", array.getName());
        //     }
        //     items.put("$ref", scalarValue(ref.getTargetType()));
        // }
        // else
        if (template instanceof LazySchemaNode lazy) {
            if (lazy.getRef() == null || lazy.getRef().isEmpty()) {
                throw new SchemaException("Lazy node has no ref: ", array.getName());
            }
            items.put("$ref", scalarValue(lazy.getRef()));
        }
        else {
            items.put("type", scalarValue(template.getType().toString().toLowerCase()));
        }

        map.put("items", items);
        return map;
    }

    // private SimpleMapNode ref(ReferenceSchemaNode ref) {
    //     SimpleMapNode map = new SimpleMapNode();
    //     if (ref.getRef() != null) {
    //         System.out.println("Unexpected");
    //     }
    //     map.put("$ref", scalarValue(ref.getTargetType()));
    //     return map;
    // }

    private SimpleMapNode scalar(SchemaNode node) {
        SimpleMapNode map = new SimpleMapNode();
        String type = node.getType().toString().toLowerCase();
        map.put("type", scalarValue(type));
        return map;
    }

    private SimpleScalarNode scalarValue(Object value) {
        SimpleScalarNode scalar = new SimpleScalarNode(value);
        if (value instanceof String) {
            scalar.setQuoteStyle(QuoteStyle.DOUBLE);
        }
        return scalar;
    }

    private Map<String,String> standardKeywords(SchemaNode compiled) {
        // TODO:
        //  - format
        //  - custom hint

        Map<String,String> map = new HashMap<>();

        if (compiled.getTitle() != null && !compiled.getTitle().isEmpty()) {
            map.put(TITLE, compiled.getTitle());
        }
        if (compiled.getDescription() != null && !compiled.getDescription().isEmpty()) {
            map.put(DESCRIPTION, compiled.getDescription());
        }

        if (compiled.isReadOnly()) { map.put(READONLY, TRUE); }
        if (compiled.getDefaultValue() != null) {
            map.put(DEFAULT, String.valueOf(compiled.getDefaultValue()));
        }
        return map;
    }

    private Map<String, Object> customHints(SchemaNode compiled) {
        Map<String, Object> map = new HashMap<>();
        compiled.getCustomHints().forEach((key,value) -> {
            map.put(key, value);
        });

        // if (compiled.isHidden()) { map.put(X_HIDDEN, TRUE); }
        // String optionProvider = compiled.getOptionProvider();
        // if (optionProvider != null) { map.put(X_PROVIDER, optionProvider); }
        // String providerParam = compiled.getProviderParameter();
        // if (providerParam != null) { map.put(X_PARAMETER, providerParam); }
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
                // assuming EnumRule exposes getOptions()
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
                // assuming RequiredRule exposes getFields()
                target.put("required", sequenceOf(req.getRequiredKeys()));
            }
        }
    }
}
