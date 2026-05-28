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
}
