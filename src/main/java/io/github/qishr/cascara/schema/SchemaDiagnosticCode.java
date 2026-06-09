package io.github.qishr.cascara.schema;

import io.github.qishr.cascara.common.diagnostic.code.DiagnosticCode;

public enum SchemaDiagnosticCode implements DiagnosticCode {
    ERROR("SCHEMA-101", "Error: {0}"),

    UNIMPLEMENTED("SCHEMA-102", "Unimplemented: {0}"),
    INVALID_SCHEMA_URI("SCHEMA-103", "Not a valid schema URI: {0}."),
    UNRECOGNIZED_LIFECYCLE("SCHEMA-104", "Unrecognized schema lifecycle: {0}."),
    MISSING_MODULE_NAME("SCHEMA-105", "Missing module name"),
    MISSING_SCHEMA_NAME("SCHEMA-106", "Missing schema name"),
    MISSING_VERSION("SCHEMA-107", "Missing version"),

    COMPILER("SCHEMA-201", "Error: {0}"),
    ROOT_MUST_BE_MAP("SCHEMA-202", "Document root must be a map"),
    NO_ID("SCHEMA-203", "Document must contain $id or origin URI must be given to compiler"),

    DECOMPILER("SCHEMA-301", "Error: {0}"),
    MISSING_REF("SCHEMA-302", "Missing $ref: {0}"),

    GENERATOR("SCHEMA-401", "Error: {0}"),
    NOT_OBJECT("SCHEMA-402", "Path does not resolve to an object: {0}."),

    RESOLVER("SCHEMA-501", "Error: {0}"),
    RESOLUTION_FAILED("SCHEMA-502", "Resolution failed"),
    LOCAL_RESOLUTION_FAILED("SCHEMA-503", "Could not resolve local $schema: {0}"),
    NODE_NOT_FOUND("SCHEMA-504", "Could not find node for fragment {0}"),
    META_INITIALIZATION_FAILURE("SCHEMA-505", "Failed to initialize built-in meta-schemas."),

    STORE("SCHEMA-601", "Error: {0}"),
    FAILED_TO_STORE("SCHEMA-602", "Failed to store schema: {0}"),
    NOT_FOUND("SCHEMA-603", "Schema not found: {0}"),
    DYNAMIC_NOT_ALLOWED("SCHEMA-604", "Dynamic Lifecycle not allowed in SchemaStore");

    private final String code;
    private final String message;

    SchemaDiagnosticCode(String code, String message) {
        this.code = code;
        this.message = message;
    }

    @Override public String getCode() { return code; }
    @Override public String getMessage() { return message; }
}