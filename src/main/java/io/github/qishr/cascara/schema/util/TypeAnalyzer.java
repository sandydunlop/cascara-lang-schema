package io.github.qishr.cascara.schema.util;

import java.lang.reflect.Field;

import io.github.qishr.cascara.common.lang.reference.ReferenceMapNode;

public interface TypeAnalyzer {
    /// Inspects a field and adds "hints" to the MapAstNode
    /// being built before the compiler sees it.
    void analyze(Field field, ReferenceMapNode targetAst);
    void analyze(Class<?> clazz, ReferenceMapNode targetAst);
}