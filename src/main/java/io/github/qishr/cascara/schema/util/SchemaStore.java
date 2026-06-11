package io.github.qishr.cascara.schema.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.qishr.cascara.common.content.ResourceContent;
import io.github.qishr.cascara.common.diagnostic.code.GenericDiagnosticCode;
import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.util.ContentTypes;
import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.lang.json.processor.JsonConverter;
import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.SchemaDiagnosticCode;
import io.github.qishr.cascara.schema.SchemaException;
import io.github.qishr.cascara.schema.util.CascaraSchemaUri.Lifecycle;

public class SchemaStore {

    private static SchemaStore instance;
    private static final Path cascaraDir = Paths.get(System.getProperty("user.home")).resolve(".cascara");
    private static final Path schemasDir = cascaraDir.resolve("schemas");

    public static SchemaStore instance() {
        if (instance == null) {
            instance = new SchemaStore();
        }
        return instance;
    }

    public ResourceContent get(CascaraSchemaUri schemaUri) throws SchemaException {
        // TODO:
        // prevent ../../ being used to look outside the store

        if (schemaUri.getLifecycle() == Lifecycle.DYNAMIC) {
            throw illegalLifecycle(schemaUri);
        }

        Path schemaFile = getPath(schemaUri).resolve("schema.json");
        if (!Files.exists(schemaFile)) {
            throw notFound(schemaUri);
        }

        String schemaSource;
        try {
            schemaSource = Files.readString(schemaFile);
        } catch (IOException e) {
            throw new SchemaException(schemaUri.toUri(), GenericDiagnosticCode.IO_ERROR, e.getMessage());
        }

        ContentType contentType = ContentTypes.find("application/schema+json");
        ResourceContent rc = new ResourceContent(schemaSource, contentType);
        return rc;
    }

    public void put(CascaraSchemaUri schemaUri, Schema compiled) {
        if (schemaUri.getLifecycle() == Lifecycle.DYNAMIC) {
            throw illegalLifecycle(schemaUri);
        }

        SchemaDecompiler decompiler = new SchemaDecompiler();
        AstNode doc = decompiler.decompile(compiled);

        String schemaString = new JsonConverter().toText(doc);

        Path schemaDir = getPath(schemaUri);
        try {
            if (!Files.exists(schemaDir)) {
                Files.createDirectories(schemaDir);
            }
            Path path = schemaDir.resolve("schema.json");
            Files.writeString(path, schemaString);
        } catch (IOException e) {
            e.printStackTrace();
            throw new SchemaException(schemaUri.toUri(), e, SchemaDiagnosticCode.FAILED_TO_STORE, e.getMessage());
        }
    }

    private Path getPath(CascaraSchemaUri schemaUri) throws SchemaException {
        Path moduleDir = schemasDir.resolve(schemaUri.getModuleName());
        Path schemaDir = moduleDir.resolve(schemaUri.getSchemaName());

        Path versionDir;
        if (schemaUri.getLifecycle() == Lifecycle.RESOURCE) {
            // TODO: Find latest version
            throw new SchemaException(schemaUri.toUri(), SchemaDiagnosticCode.UNIMPLEMENTED, "Lifecycle.RESOURCE");
        } else {
            versionDir = schemaDir.resolve(schemaUri.getVersion());
            return versionDir;
        }
    }

    private SchemaException notFound(CascaraSchemaUri schemaUri) {
        return new SchemaException(schemaUri.toUri(), SchemaDiagnosticCode.NOT_FOUND, schemaUri);
    }

    private SchemaException illegalLifecycle(CascaraSchemaUri schemaUri) {
        return new SchemaException(schemaUri.toUri(), SchemaDiagnosticCode.DYNAMIC_NOT_ALLOWED);
    }
}
