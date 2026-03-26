package io.github.qishr.cascara.schema.ast;

public enum SchemaType {
    STRING, BOOLEAN, INTEGER, NUMBER, OBJECT, ARRAY, NULL;

    public static SchemaType fromString(String type) {
        try {
            return valueOf(type.toUpperCase());
        } catch (IllegalArgumentException e) {
            return OBJECT; // Default fallback
        }
    }
}