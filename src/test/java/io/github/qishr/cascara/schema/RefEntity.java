package io.github.qishr.cascara.schema;

import java.util.List;

import io.github.qishr.cascara.schema.annotation.SchemaField;

public class RefEntity {
    @SchemaField(title = "Child")
    SimpleEntity child;

    @SchemaField(title = "Children")
    List<RefEntity> children;
}
