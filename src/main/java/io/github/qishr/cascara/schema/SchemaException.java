package io.github.qishr.cascara.schema;

import java.net.URI;

import io.github.qishr.cascara.common.lang.exception.LocatableException;

public class SchemaException extends RuntimeException implements LocatableException {
    private final String schemaPath;
    private int line = -1;
    private int column = -1;
    private URI uri;
    private final String rawMessage;

    /**
     * Standard constructor for logical schema errors.
     */
    public SchemaException(String message, String schemaPath) {
        super(message + " for " + schemaPath);
        this.rawMessage = message;
        this.schemaPath = schemaPath;
    }

    public SchemaException(String message, String schemaPath, URI uri) {
        super(message + String.format(" for %s (at %s)", schemaPath, uri));
        this.schemaPath = schemaPath;
        this.rawMessage = message;
    }

    public SchemaException(String message, String schemaPath, int line, int column, URI uri) {
        super(message + String.format(" for %s (at %s:%d)", schemaPath, uri, line));
        this.schemaPath = schemaPath;
        this.rawMessage = message;
        this.line = line;
        this.column = column;
        this.uri = uri;
    }

    public SchemaException(String message, int line, int column, URI uri) {
        super(message + String.format(" (at %s:%d)", uri, line));
        this.schemaPath = null;
        this.rawMessage = message;
        this.line = line;
        this.column = column;
        this.uri = uri;
    }

    /**
     * The "Context-Aware" wrapper.
     * It checks if the cause is Locatable and inherits its coordinates.
     */
    public SchemaException(String message, Throwable cause, String schemaPath) {
        super(message + (schemaPath != null ? " (at " + schemaPath + ")" : ""), cause);
        this.schemaPath = schemaPath != null ? schemaPath : "unknown";
        this.rawMessage = message;

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
    public String getRawMessage() { return rawMessage; }
}