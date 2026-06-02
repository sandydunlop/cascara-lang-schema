package io.github.qishr.cascara.schema;

public enum SchemaType {
    ANY, STRING, BOOLEAN, INTEGER, NUMBER, OBJECT, ARRAY, NULL;

    public static SchemaType fromString(String type) {
        try {
            return valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OBJECT; // Default fallback
        }
    }

    /// Return the JSON Schema type name
    public String asString() {
        return toString().toLowerCase();
    }
}