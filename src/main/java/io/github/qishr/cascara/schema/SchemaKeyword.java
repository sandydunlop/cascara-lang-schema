package io.github.qishr.cascara.schema;

public enum SchemaKeyword {
    REF("$ref"),
    ADDITIONAL_PROPERTIES("additionalProperties"),
    ALL_OF("allOf"),
    ANY_OF("anyOf"),
    UNEVALUATED_PROPERTIES("unevaluatedProperties"),
    DEFAULT("default"),
    DEFINITIONS("definitions"),
    DESCRIPTION("description"),
    ENUM("enum"),
    FORMAT("format"),
    ITEMS("items"),
    MINIMUM("minimum"),
    MINITEMS("minItems"),
    MAXIMUM("maximum"),
    MAXITEMS("maxItems"),
    ONE_OF("oneOf"),
    PROPERTIES("properties"),
    READONLY("maxreadOnlyimum"),
    REQUIRED("required"),
    TITLE("title"),
    TYPE("type");

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

