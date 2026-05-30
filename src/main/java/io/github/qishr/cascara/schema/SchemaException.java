package io.github.qishr.cascara.schema;

import java.net.URI;

import io.github.qishr.cascara.common.lang.exception.LocatableException;

public class SchemaException extends LocatableException {
    private static final int UNKNOWN_COORD = LocatableException.UNKNOWN_COORD;

    private final String schemaPath;
    private final String rawMessage;

    /// For errors in a schema for a class.
    public SchemaException(String message, Class<?> clazz) {
        this(
            message, message + " for " + clazz,
            null, null, null, UNKNOWN_COORD, UNKNOWN_COORD, null
        );
    }

    /// For errors in a schema caused by an exception.
    public SchemaException(String message, Throwable cause, URI uri) {
        this(
            message, uri == null ? message : message + " (" + uri + ")",
            cause, null, null, UNKNOWN_COORD, UNKNOWN_COORD, uri
        );
    }

    /// For errors in a schema.
    public SchemaException(String message, URI uri) {
        this(
            message, uri == null ? message : message + " (" + uri + ")",
            null, null, null, UNKNOWN_COORD, UNKNOWN_COORD, uri
        );
    }

    /// For errors in a schema where the line and column are known.
    public SchemaException(String message, int line, int column, URI uri) {
        this(
            message, message + String.format(" (at %s:%d)", uri, line),
            null, null, null, line, column, uri
        );
    }

    /// For errors relating to a path in a compiled schema.
    public SchemaException(String message, String schemaPath, URI uri) {
        this(
            message, message + String.format(" for %s (in %s)", schemaPath, uri),
            null, null, schemaPath, UNKNOWN_COORD, UNKNOWN_COORD, uri
        );
    }

    /// For errors relating to a path in a schema where the line and column are known.
    public SchemaException(String message, String schemaPath, int line, int column, URI uri) {
        this(
            message, message + String.format(" for %s (at %s:%d)", schemaPath, uri, line),
            null, null, schemaPath, line, column, uri
        );
    }

    private SchemaException(String rawMessage, String message, Throwable cause, Class<?> clazz, String schemaPath, int line, int column, URI uri) {
        super(message, cause, line, column, uri);
        this.rawMessage = rawMessage;
        this.schemaPath = schemaPath;
    }

    public String getSchemaPath() { return schemaPath; }
    public String getRawMessage() { return rawMessage; }
}