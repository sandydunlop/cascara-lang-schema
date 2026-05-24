package io.github.qishr.cascara.schema.util;

import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.service.ServiceProvider;
import io.github.qishr.cascara.schema.Schema;

public interface SchemaDecompiler extends ServiceProvider {
    SimpleDocument decompile(Schema compiled);
}
