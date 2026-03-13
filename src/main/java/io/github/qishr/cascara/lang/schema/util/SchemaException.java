package io.github.qishr.cascara.lang.schema.util;

import java.net.URI;

import io.github.qishr.cascara.common.lang.exception.LocatableException;

public class SchemaException extends RuntimeException implements LocatableException {
    private final String schemaPath;
    private int line = -1;
    private int column = -1;
    private URI uri;

    /**
     * Standard constructor for logical schema errors.
     */
    public SchemaException(String message, String schemaPath) {
        super(message + " (at " + schemaPath + ")");
        this.schemaPath = schemaPath;
    }

    /**
     * The "Context-Aware" wrapper.
     * It checks if the cause is Locatable and inherits its coordinates.
     */
    public SchemaException(String message, Throwable cause, String schemaPath) {
        super(message + (schemaPath != null ? " (at " + schemaPath + ")" : ""), cause);
        this.schemaPath = schemaPath != null ? schemaPath : "unknown";

        if (cause instanceof LocatableException loc) {
            this.line = loc.getLine();
            this.column = loc.getColumn();
            this.uri = loc.getUri();
        }
    }

    @Override public int getLine() { return line; }
    @Override public int getColumn() { return column; }
    @Override public URI getUri() { return uri; }
    public String getSchemaPath() { return schemaPath; }
}