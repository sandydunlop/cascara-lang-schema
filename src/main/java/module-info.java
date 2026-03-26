module cascara.schema {
    requires transitive cascara.common;
    requires cascara.lang.json;

    exports io.github.qishr.cascara.schema;
    exports io.github.qishr.cascara.schema.annotation;
    exports io.github.qishr.cascara.schema.api;
    exports io.github.qishr.cascara.schema.ast;
    exports io.github.qishr.cascara.schema.constraint;
    exports io.github.qishr.cascara.schema.rule;
    exports io.github.qishr.cascara.schema.util;

    opens io.github.qishr.cascara.schema;
    opens io.github.qishr.cascara.schema.ast;
    opens io.github.qishr.cascara.schema.constraint;
    opens io.github.qishr.cascara.schema.rule;
    opens io.github.qishr.cascara.schema.util;
}
