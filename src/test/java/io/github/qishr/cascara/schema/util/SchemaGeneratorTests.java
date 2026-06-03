package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertTrue;

import java.time.LocalDateTime;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.reference.ReferenceDocument;
import io.github.qishr.cascara.schema.annotation.SchemaDefinition;
import io.github.qishr.cascara.schema.annotation.SchemaProperty;

public class SchemaGeneratorTests {

    @SchemaDefinition
    public static class TestClass {
        @SchemaProperty
        private LocalDateTime dateTime;
    }

    @Test
    void t1() {
        SchemaGenerator generator = new SchemaGenerator();
        ReferenceDocument schemaDoc = generator.generate(TestClass.class);
        assertTrue(schemaDoc != null);
    }
}
