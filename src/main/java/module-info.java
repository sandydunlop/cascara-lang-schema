module cascara.schema {
    requires transitive cascara.common;
    requires transitive cascara.common.io;

    requires cascara.lang.json;

    exports io.github.qishr.cascara.schema;
    exports io.github.qishr.cascara.schema.annotation;
    exports io.github.qishr.cascara.schema.structure;
    exports io.github.qishr.cascara.schema.constraint;
    exports io.github.qishr.cascara.schema.rule;
    exports io.github.qishr.cascara.schema.util;

    exports io.github.qishr.cascara.schema.internal to cascara.common;

    provides io.github.qishr.cascara.schema.util.SchemaCompiler
        with io.github.qishr.cascara.schema.internal.CascaraSchemaCompiler;

    provides io.github.qishr.cascara.schema.util.SchemaDecompiler
        with io.github.qishr.cascara.schema.internal.CascaraSchemaDecompiler;

    provides io.github.qishr.cascara.schema.util.SchemaGenerator
        with io.github.qishr.cascara.schema.internal.CascaraSchemaGenerator;

    provides io.github.qishr.cascara.schema.util.SchemaResolver
        with io.github.qishr.cascara.schema.internal.CascaraSchemaResolver;
}
