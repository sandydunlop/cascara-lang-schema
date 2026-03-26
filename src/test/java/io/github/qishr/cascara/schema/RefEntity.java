package io.github.qishr.cascara.schema;

import java.util.List;

import io.github.qishr.cascara.schema.annotation.SchemaProperty;

public class RefEntity {
    @SchemaProperty(title = "Child")
    SimpleEntity child;

    @SchemaProperty(title = "Children")
    List<RefEntity> children;
}
