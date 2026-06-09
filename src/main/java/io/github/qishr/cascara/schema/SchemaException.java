package io.github.qishr.cascara.schema;

import java.net.URI;

import io.github.qishr.cascara.common.diagnostic.LocalizableException;
import io.github.qishr.cascara.common.diagnostic.LocatableException;
import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;

public class SchemaException extends LocatableException {
    private static final int UNKNOWN_COORD = LocatableException.UNKNOWN_COORD;

    private final String schemaPath;
    private final Class<?> clazz;

    /// For errors in a schema for a class.
    public SchemaException(Class<?> clazz, DiagnosticCode code, Object... details) {
        this(
            null, null,  UNKNOWN_COORD, UNKNOWN_COORD, clazz, null, code, details
        );
    }

    /// For errors not inside a schema file.
    public SchemaException(DiagnosticCode code, Object... details) {
        this(
            null, null,  UNKNOWN_COORD, UNKNOWN_COORD, null, null, code, details
        );
    }

    /// For errors in a schema caused by an exception.
    public SchemaException(URI uri, Throwable cause, DiagnosticCode code, Object... details) {
        this(
            uri, null,  UNKNOWN_COORD, UNKNOWN_COORD, null, cause, code, details
        );
    }

    /// For errors in a schema.
    public SchemaException(URI uri, DiagnosticCode code, Object... details) {
        this(
            uri, null, UNKNOWN_COORD, UNKNOWN_COORD, null, null, code, details
        );
    }

    /// For errors in a schema where the line and column are known.
    public SchemaException(URI uri, int line, int column, DiagnosticCode code, Object... details) {
        this(
            uri, null, line, column, null, null, code, details
        );
    }

    /// For errors relating to a path in a compiled schema.
    public SchemaException(URI uri, String schemaPath, DiagnosticCode code, Object... details) {
        this(
            uri, schemaPath, UNKNOWN_COORD, UNKNOWN_COORD, null, null, code, details
        );
    }

    /// For errors relating to a path in a schema where the line and column are known.
    public SchemaException(URI uri, String schemaPath, int line, int column, DiagnosticCode code, Object... details) {
        this(
            uri, schemaPath, line, column, null, null, code, details
        );
    }

    public SchemaException(LocalizableException cause) {
        this(
            null, null,  UNKNOWN_COORD, UNKNOWN_COORD, null, cause.getCause(), cause.getCode(), cause.getDetails()
        );
    }


    private SchemaException(URI uri, String schemaPath, int line, int column, Class<?> clazz, Throwable cause, DiagnosticCode code, Object... details) {
        super(uri, line, column, cause, code);
        this.schemaPath = schemaPath;
        this.clazz = clazz;
    }

    public String getSchemaPath() { return schemaPath; }
    public Class<?> getType() { return clazz; }
}