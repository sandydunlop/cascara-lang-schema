package io.github.qishr.cascara.schema.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.qishr.cascara.common.util.ContentType;
import io.github.qishr.cascara.common.content.ResourceContent;
import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.spi.ContentTypes;
import io.github.qishr.cascara.lang.json.processor.JsonConverter;
import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.SchemaException;
import io.github.qishr.cascara.schema.internal.CascaraSchemaDecompiler;
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
            throw new SchemaException(e.getMessage(), e, schemaUri.toUri());
        }

        ContentType contentType = ContentTypes.find("application/schema+json");
        ResourceContent rc = new ResourceContent(schemaSource, contentType);
        return rc;
    }

    public void put(CascaraSchemaUri schemaUri, Schema compiled) {
        if (schemaUri.getLifecycle() == Lifecycle.DYNAMIC) {
            throw illegalLifecycle(schemaUri);
        }

        CascaraSchemaDecompiler decompiler = new CascaraSchemaDecompiler();
        StructuredDocument doc = decompiler.decompile(compiled);

        // AstConverter<?> converter = new AstConverterFactory().create("application/schema+json");
        // String schemaString = converter.toText(doc.getRoot());

        String schemaString = new JsonConverter().toText(doc.getRoot());

        Path schemaDir = getPath(schemaUri);
        try {
            if (!Files.exists(schemaDir)) {
                Files.createDirectories(schemaDir);
            }
            Path path = schemaDir.resolve("schema.json");
            Files.writeString(path, schemaString);
        } catch (IOException e) {
            e.printStackTrace();
            throw new SchemaException("Failed to store schema: " + e.getMessage(), e, schemaUri.toUri());
        }
    }

    private Path getPath(CascaraSchemaUri schemaUri) throws SchemaException {
        Path schemaDir = schemasDir.resolve(schemaUri.getModuleName(), schemaUri.getSchemaName());

        Path versionDir;
        if (schemaUri.getLifecycle() == Lifecycle.RESOURCE) {
            // TODO: Find latest version
            throw new SchemaException("Unimplemented: Lifecycle.RESOURCE", schemaUri.toUri());
        } else {
            versionDir = schemaDir.resolve(schemaUri.getVersion());
            return versionDir;
        }
    }

    private SchemaException notFound(CascaraSchemaUri uri) {
        return new SchemaException("Schema not found", uri.toUri());
    }

    private SchemaException illegalLifecycle(CascaraSchemaUri schemaUri) {
        return new SchemaException("Dynamic Lifecycle not allowed in SchemaStore", schemaUri.toUri());
    }
}
