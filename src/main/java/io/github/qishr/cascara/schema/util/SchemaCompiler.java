package io.github.qishr.cascara.schema.util;

import java.net.URI;

import io.github.qishr.cascara.common.diagnostic.Reporter;
import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.service.ServiceProvider;
import io.github.qishr.cascara.schema.Schema;

public interface SchemaCompiler extends ServiceProvider {

    Schema compile(StructuredDocument doc);
    Schema compile(StructuredDocument doc, URI originUri);

    SchemaCompiler setResolver(SchemaResolver resolver);
    SchemaCompiler setReporter(Reporter reporter);

}