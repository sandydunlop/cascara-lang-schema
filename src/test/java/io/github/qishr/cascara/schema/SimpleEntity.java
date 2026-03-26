package io.github.qishr.cascara.schema;

import io.github.qishr.cascara.schema.annotation.SchemaProperty;

public class SimpleEntity {
    @SchemaProperty(title = "Name")
    String name;

    @SchemaProperty(title = "Age")
    int age;
}

