package io.github.qishr.cascara.schema.util;

import java.net.URI;

import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.api.SchemaCompiler;
import io.github.qishr.cascara.schema.api.SchemaResolver;
import io.github.qishr.cascara.schema.api.TypeAnalyzer;

public class SchemaBuilder {
    ClassSchemaGenerator generator;
    SchemaResolver resolver;
    SchemaCompiler compiler;

    public SchemaBuilder(SchemaResolver resolver) {
        this.resolver = resolver;
        compiler = new CascaraSchemaCompiler(resolver);
        generator = new ClassSchemaGenerator();
    }

    public void registerTypeAnalyzer(TypeAnalyzer ta) {
        generator.registerTypeAnalyzer(ta);
    }

    public CompiledSchema buildSchema(String name, URI originUri, Class<?>... classes)  {
        SimpleMapNode syntheticRoot;
        SimpleDocument syntheticRootDoc;
        CompiledSchema compiledSchema;
        SimpleMapNode definitions = new SimpleMapNode();
        syntheticRoot = new SimpleMapNode();
        syntheticRoot.put("definitions", definitions);
        syntheticRoot.put("$id", new SimpleScalarNode(originUri));
        syntheticRoot.put("name", new SimpleScalarNode(name));

        for (Class<?> cls : classes) {
            // Give the generator the synthentic root AST and
            // tell it where generated definitions go within it.
            generator.generate(syntheticRoot, "#/definitions", cls);
        }

        syntheticRootDoc = new SimpleDocument(syntheticRoot);
        compiledSchema = compiler.compile(syntheticRootDoc);

        return compiledSchema;
    }
}
