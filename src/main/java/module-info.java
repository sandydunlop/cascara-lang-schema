module cascara.lang.schema {
    requires transitive cascara.common;

    exports io.github.qishr.cascara.lang.schema;
    exports io.github.qishr.cascara.lang.schema.annotation;
    exports io.github.qishr.cascara.lang.schema.api;
    exports io.github.qishr.cascara.lang.schema.ast;
    exports io.github.qishr.cascara.lang.schema.constraint;
    // exports io.github.qishr.cascara.lang.schema.extension;
    // exports io.github.qishr.cascara.persistence.sqlite.diff;
    // exports io.github.qishr.cascara.lang.schema.processor;
    exports io.github.qishr.cascara.lang.schema.rule;
    exports io.github.qishr.cascara.lang.schema.util;

    opens io.github.qishr.cascara.lang.schema;
    opens io.github.qishr.cascara.lang.schema.ast;
    opens io.github.qishr.cascara.lang.schema.constraint;
    // opens io.github.qishr.cascara.lang.schema.extension;
    opens io.github.qishr.cascara.lang.schema.rule;
    opens io.github.qishr.cascara.lang.schema.util;
}
