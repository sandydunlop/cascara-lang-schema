package io.github.qishr.cascara.schema.api;

import java.net.URI;

import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.ast.SchemaNode;

public interface SchemaCompiler {

    CompiledSchema compile(StructuredDocument doc);
    CompiledSchema compile(StructuredDocument doc, URI originUri);

    /// Compiles a specific part of a document (e.g., a fragment resolution).
    SchemaNode compileSubSchema(MapAstNode<?,?> node, URI originUri);

}