package io.github.qishr.cascara.schema.util;

import java.net.URI;

import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.schema.CompiledSchema;
import io.github.qishr.cascara.schema.SchemaKeyword;
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

    public CompiledSchema buildSchema(URI originUri, Class<?>... classes)  {
        SimpleDocument syntheticRootDoc;
        CompiledSchema compiledSchema;
        SimpleMapNode syntheticRoot = new SimpleMapNode();
        SimpleMapNode definitions = new SimpleMapNode();

        syntheticRoot.put(SchemaKeyword.DEFS.string(), definitions);
        syntheticRoot.put(SchemaKeyword.ID.string(), new SimpleScalarNode(originUri));

        for (Class<?> clazz : classes) {
            // Give the generator the synthentic root AST and
            // tell it where generated definitions go within it.
            String fragment = "#/" + SchemaKeyword.DEFS.string();
            generator.generate(syntheticRoot, fragment, clazz);
        }

        syntheticRootDoc = new SimpleDocument(syntheticRoot);
        compiledSchema = compiler.compile(syntheticRootDoc);

        return compiledSchema;
    }
}
