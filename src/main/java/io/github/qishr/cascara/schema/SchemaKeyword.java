package io.github.qishr.cascara.schema;

public enum SchemaKeyword {
    // Core Identifiers
    ID("$id"),
    SCHEMA("$schema"),
    ANCHOR("$anchor"),
    DYNAMIC_REF("$dynamicRef"),
    DYNAMIC_ANCHOR("$dynamicAnchor"),
    VOCABULARY("$vocabulary"),

    // Sub-schema Containers
    DEFS("$defs"),
    DEFINITIONS("definitions"), // Legacy support
    REF("$ref"),

    // Logic and Conditional
    ALL_OF("allOf"),
    ANY_OF("anyOf"),
    ONE_OF("oneOf"),
    NOT("not"),
    IF("if"),
    THEN("then"),
    ELSE("else"),
    DEPENDENT_SCHEMAS("dependentSchemas"),
    DEPENDENT_REQUIRED("dependentRequired"),

    // Object Validation
    PROPERTIES("properties"),
    PATTERN_PROPERTIES("patternProperties"),
    ADDITIONAL_PROPERTIES("additionalProperties"),
    UNEVALUATED_PROPERTIES("unevaluatedProperties"),
    REQUIRED("required"),
    PROPERTY_NAMES("propertyNames"),
    MIN_PROPERTIES("minProperties"),
    MAX_PROPERTIES("maxProperties"),

    // Array Validation
    ITEMS("items"),
    PREFIX_ITEMS("prefixItems"),
    UNEVALUATED_ITEMS("unevaluatedItems"),
    CONTAINS("contains"),
    MIN_CONTAINS("minContains"),
    MAX_CONTAINS("maxContains"),
    MIN_ITEMS("minItems"),
    MAX_ITEMS("maxItems"),
    UNIQUE_ITEMS("uniqueItems"),

    // Scalar Validation
    TYPE("type"),
    ENUM("enum"),
    CONST("const"),
    MULTIPLE_OF("multipleOf"),
    MAXIMUM("maximum"),
    EXCLUSIVE_MAXIMUM("exclusiveMaximum"),
    MINIMUM("minimum"),
    EXCLUSIVE_MINIMUM("exclusiveMinimum"),
    MAX_LENGTH("maxLength"),
    MIN_LENGTH("minLength"),
    PATTERN("pattern"),

    // Metadata & Documentation
    TITLE("title"),
    DESCRIPTION("description"),
    DEFAULT("default"),
    DEPRECATED("deprecated"),
    READ_ONLY("readOnly"),
    WRITE_ONLY("writeOnly"),
    FORMAT("format"),
    CONTENT_MEDIA_TYPE("contentMediaType"),
    CONTENT_ENCODING("contentEncoding");

    private final String string;
    SchemaKeyword(String string) { this.string = string; }
    public String string() { return string; }

    public static SchemaKeyword fromString(String s) {
        SchemaKeyword keyword = get(s);
        if (s == null) {
            throw new IllegalArgumentException("No enum constant with value " + s);
        }
        return keyword;
    }

    public static boolean exists(String s) {
        return get(s) != null;
    }

    private static SchemaKeyword get(String s) {
        for (SchemaKeyword keyword : values()) {
            if (keyword.string.equalsIgnoreCase(s)) {
                return keyword;
            }
        }
        return null;
    }
}

