package io.github.qishr.cascara.schema;

import java.util.Collections;
import java.util.List;

public enum SchemaKeyword {
    // Core Identifiers
    ID("$id", SchemaType.STRING),
    SCHEMA("$schema", SchemaType.STRING),
    ANCHOR("$anchor", SchemaType.STRING),
    DYNAMIC_REF("$dynamicRef", SchemaType.STRING),
    DYNAMIC_ANCHOR("$dynamicAnchor", SchemaType.STRING),
    VOCABULARY("$vocabulary", SchemaType.OBJECT),

    // Sub-schema Containers
    DEFS("$defs", SchemaType.OBJECT),
    DEFINITIONS("definitions", SchemaType.OBJECT), // Legacy support
    REF("$ref", SchemaType.STRING),

    // Logic and Conditional
    ALL_OF("allOf", SchemaType.ARRAY),
    ANY_OF("anyOf", SchemaType.ARRAY),
    ONE_OF("oneOf", SchemaType.ARRAY),
    NOT("not", SchemaType.OBJECT),
    IF("if", SchemaType.OBJECT),
    THEN("then", SchemaType.OBJECT),
    ELSE("else", SchemaType.OBJECT),
    DEPENDENT_SCHEMAS("dependentSchemas", SchemaType.OBJECT),
    DEPENDENT_REQUIRED("dependentRequired", SchemaType.OBJECT),

    // Object Validation
    PROPERTIES("properties", SchemaType.OBJECT),
    PATTERN_PROPERTIES("patternProperties", SchemaType.OBJECT),
    ADDITIONAL_PROPERTIES("additionalProperties", SchemaType.OBJECT),
    UNEVALUATED_PROPERTIES("unevaluatedProperties", SchemaType.OBJECT),
    REQUIRED("required", SchemaType.ARRAY),
    PROPERTY_NAMES("propertyNames", SchemaType.OBJECT),
    MIN_PROPERTIES("minProperties", SchemaType.INTEGER),
    MAX_PROPERTIES("maxProperties", SchemaType.INTEGER),

    // Array Validation
    ITEMS("items", SchemaType.ANY), // Can be Object or Array (Draft 7)
    PREFIX_ITEMS("prefixItems", SchemaType.ARRAY),
    UNEVALUATED_ITEMS("unevaluatedItems", SchemaType.OBJECT),
    CONTAINS("contains", SchemaType.OBJECT),
    MIN_CONTAINS("minContains", SchemaType.INTEGER),
    MAX_CONTAINS("maxContains", SchemaType.INTEGER),
    MIN_ITEMS("minItems", SchemaType.INTEGER),
    MAX_ITEMS("maxItems", SchemaType.INTEGER),
    UNIQUE_ITEMS("uniqueItems", SchemaType.BOOLEAN),

    // Scalar Validation
    // TYPE("type", SchemaType.STRING),
    TYPE("type", SchemaType.STRING, List.of(
        "string", "number", "integer", "boolean", "object", "array", "null"
    )),
    ENUM("enum", SchemaType.ARRAY),
    CONST("const", SchemaType.ANY),
    MULTIPLE_OF("multipleOf", SchemaType.NUMBER),
    MAXIMUM("maximum", SchemaType.NUMBER),
    EXCLUSIVE_MAXIMUM("exclusiveMaximum", SchemaType.NUMBER),
    MINIMUM("minimum", SchemaType.NUMBER),
    EXCLUSIVE_MINIMUM("exclusiveMinimum", SchemaType.NUMBER),
    MAX_LENGTH("maxLength", SchemaType.INTEGER),
    MIN_LENGTH("minLength", SchemaType.INTEGER),
    PATTERN("pattern", SchemaType.STRING),

    // Metadata & Documentation
    TITLE("title", SchemaType.STRING),
    DESCRIPTION("description", SchemaType.STRING),
    DEFAULT("default", SchemaType.ANY),
    DEPRECATED("deprecated", SchemaType.BOOLEAN),

    READ_ONLY("readOnly", SchemaType.BOOLEAN),


    WRITE_ONLY("writeOnly", SchemaType.BOOLEAN),
    FORMAT("format", SchemaType.STRING),
    CONTENT_MEDIA_TYPE("contentMediaType", SchemaType.STRING),
    CONTENT_ENCODING("contentEncoding", SchemaType.STRING);

    private final String string;
    private final SchemaType type;
    private final List<String> suggestions;

    SchemaKeyword(String string, SchemaType type) {
        this(string, type, Collections.emptyList());
    }

    SchemaKeyword(String string, SchemaType type, List<String> suggestions) {
        this.string = string;
        this.type = type;
        this.suggestions = suggestions;
    }

    /// Returns the JSON Schema keyword name
    public String string() { return string; }

    public SchemaType type() { return type; }
    public List<String> suggestions() { return suggestions; }
    public boolean hasSuggestions() { return !suggestions.isEmpty(); }

    public static SchemaKeyword fromString(String s) {
        SchemaKeyword keyword = get(s);
        if (keyword == null) {
            throw new IllegalArgumentException("No enum constant with value " + s);
        }
        return keyword;
    }

    public static boolean exists(String s) {
        return get(s) != null;
    }

    public static SchemaKeyword get(String s) {
        if (s == null) return null;
        for (SchemaKeyword keyword : values()) {
            if (keyword.string.equalsIgnoreCase(s)) {
                return keyword;
            }
        }
        return null;
    }
}
