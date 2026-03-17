package io.github.qishr.cascara.schema;

import io.github.qishr.cascara.schema.annotation.SchemaField;

// src/test/java/testschema/SimpleEntity.java
public class SimpleEntity {
    @SchemaField(title = "Name")
    String name;

    @SchemaField(title = "Age")
    int age;
}

