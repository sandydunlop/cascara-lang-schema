package io.github.qishr.cascara.schema.api;

import java.lang.reflect.Field;

import io.github.qishr.cascara.common.lang.ast.MapAstNode;

public interface TypeAnalyzer {
    /// Inspects a field and adds "hints" to the MapAstNode
    /// being built before the compiler sees it.
    void analyze(Field field, MapAstNode targetAst);
    void analyze(Class<?> clazz, MapAstNode targetAst);
}