package io.github.qishr.cascara.schema.util;

import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.service.ServiceProvider;

public interface SchemaGenerator extends ServiceProvider {
    public void registerTypeAnalyzer(TypeAnalyzer ta);

    public SimpleDocument generate(Object template);

    public SimpleDocument generate(Class<?> clazz);

    public SimpleDocument generate(SimpleMapNode parentDoc, Class<?> clazz);

    public SimpleDocument generate(MapAstNode<?,?> parentDoc, String fragment, Class<?> clazz);

    public SimpleDocument generate(MapAstNode<?,?> parentDoc, String fragment, Class<?> clazz, Object template);

}
