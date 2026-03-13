package io.github.qishr.cascara.lang.schema.api;

import java.lang.reflect.Field;

import io.github.qishr.cascara.common.lang.ast.MapAstNode;

public interface TypeAnalyzer {
    /** * Inspects a class or field and adds "hints" to the MapAstNode
     * being built before the compiler sees it.
     */
    // void analyze(Class<?> clazz, MapAstNode targetAst);
    void analyze(Field field, MapAstNode targetAst);
}