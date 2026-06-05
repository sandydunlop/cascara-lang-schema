package io.github.qishr.cascara.schema.util;

import java.net.URI;

import io.github.qishr.cascara.common.lang.reference.ReferenceMapNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceScalarNode;
import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.SchemaKeyword;

public class SchemaBuilder {
    SchemaGenerator generator;
    SchemaResolver resolver;
    SchemaCompiler compiler;

    public SchemaBuilder(SchemaResolver resolver) {
        this.resolver = resolver;
        compiler = new SchemaCompiler(resolver);
        generator = new SchemaGenerator();
    }

    public void registerTypeAnalyzer(TypeAnalyzer ta) {
        generator.registerTypeAnalyzer(ta);
    }

    public Schema buildSchema(URI originUri, Class<?>... classes)  {
        Schema schema;
        ReferenceMapNode syntheticRoot = new ReferenceMapNode();
        ReferenceMapNode definitions = new ReferenceMapNode();

        syntheticRoot.put(SchemaKeyword.DEFS.asString(), definitions);
        syntheticRoot.put(SchemaKeyword.ID.asString(), new ReferenceScalarNode(originUri));

        for (Class<?> clazz : classes) {
            // Give the generator the synthentic root AST and
            // tell it where generated definitions go within it.
            String fragment = "#/" + SchemaKeyword.DEFS.asString();
            generator.generate(syntheticRoot, fragment, clazz);
        }

        schema = compiler.compile(syntheticRoot, originUri);

        return schema;
    }
}
