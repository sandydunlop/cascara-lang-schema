package io.github.qishr.cascara.schema.util;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

import io.github.qishr.cascara.common.content.ContentType;
import io.github.qishr.cascara.common.content.ResourceContent;
import io.github.qishr.cascara.common.lang.processor.AstConverter;
import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.spi.AstConverterFactory;
import io.github.qishr.cascara.common.spi.ContentTypes;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.SchemaException;

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

    public ResourceContent get(CascaraSchemaUri uri) throws SchemaException {
        // TODO:
        // prevent ../../ being used to look outside the store

        Path schemaFile = getPath(uri).resolve("schema.json");
        if (!Files.exists(schemaFile)) {
            throw notFound(uri);
        }

        String schemaSource;
        try {
            schemaSource = Files.readString(schemaFile);
        } catch (IOException e) {
            // TODO Auto-generated catch block
            e.printStackTrace();
            throw new SchemaException(e.getMessage(), e, uri.getUri());
        }

        ContentType contentType = ContentTypes.find("application/json+schema");
        ResourceContent rc = new ResourceContent(schemaSource, contentType);
        return rc;
    }

    public void put(CascaraSchemaUri uri, CompiledSchema compiled) {
        CascaraSchemaDecompiler decompiler = new CascaraSchemaDecompiler();
        StructuredDocument doc = decompiler.decompile(compiled);

        AstConverter<?> converter = new AstConverterFactory().create("application/json+schema");
        String schemaString = converter.toText(doc.getRoot());

        Path schemaDir = getPath(uri);
        try {
            if (!Files.exists(schemaDir)) {
                Files.createDirectories(schemaDir);
            }
            Path path = schemaDir.resolve("schema.json");
            Files.writeString(path, schemaString);
        } catch (IOException e) {
            e.printStackTrace();
            throw new SchemaException("Failed to store schema: " + e.getMessage(), e, uri.getUri());
        }
    }

    private Path getPath(CascaraSchemaUri uri) throws SchemaException {
        Path schemaDir = schemasDir.resolve(uri.getModuleName(), uri.getSchemaName());

        Path versionDir;
        if (uri.getVersion() == null) {
            // TODO: Find latest version
            throw new SchemaException("Unimplemented - no version", "");
        } else {
            versionDir = schemaDir.resolve(uri.getVersion());
            return versionDir;
        }
    }

    private SchemaException notFound(CascaraSchemaUri uri) {
        return new SchemaException("Schema not found", uri.getUri());
    }
}
