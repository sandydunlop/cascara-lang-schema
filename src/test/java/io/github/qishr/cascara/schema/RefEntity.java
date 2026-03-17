package io.github.qishr.cascara.schema;

import java.util.List;

import io.github.qishr.cascara.schema.annotation.SchemaField;

// src/test/java/testschema/RefEntity.java
public class RefEntity {
    @SchemaField(title = "Child")
    SimpleEntity child;

    @SchemaField(title = "Children")
    List<RefEntity> children;
}
