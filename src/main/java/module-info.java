module cascara.schema {
    requires transitive cascara.common;

    exports io.github.qishr.cascara.schema;
    exports io.github.qishr.cascara.schema.annotation;
    exports io.github.qishr.cascara.schema.api;
    exports io.github.qishr.cascara.schema.ast;
    exports io.github.qishr.cascara.schema.constraint;
    // exports io.github.qishr.cascara.schema.extension;
    // exports io.github.qishr.cascara.persistence.sqlite.diff;
    // exports io.github.qishr.cascara.schema.processor;
    exports io.github.qishr.cascara.schema.rule;
    exports io.github.qishr.cascara.schema.util;

    opens io.github.qishr.cascara.schema;
    opens io.github.qishr.cascara.schema.ast;
    opens io.github.qishr.cascara.schema.constraint;
    // opens io.github.qishr.cascara.schema.extension;
    opens io.github.qishr.cascara.schema.rule;
    opens io.github.qishr.cascara.schema.util;
}
