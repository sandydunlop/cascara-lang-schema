package io.github.qishr.cascara.lang.schema.api;

import io.github.qishr.cascara.common.content.ResourceContent;
import io.github.qishr.cascara.common.lang.StructuredDocument;
import io.github.qishr.cascara.common.lang.exception.ParserException;

public interface SchemaParser {
    StructuredDocument parseContent(ResourceContent resource) throws ParserException;
}
