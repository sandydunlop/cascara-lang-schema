package io.github.qishr.cascara.schema.api;

import java.net.URI;

import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.schema.CompiledSchema;

public interface SchemaCompiler {

    CompiledSchema compile(StructuredDocument doc);
    CompiledSchema compile(StructuredDocument doc, URI originUri);

}