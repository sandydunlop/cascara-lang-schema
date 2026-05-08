package io.github.qishr.cascara.schema.util;

import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.schema.annotation.SchemaProperty;

import org.junit.jupiter.api.Test;

import java.net.URI;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class ClassSchemaGeneratorTest {

    static class SimpleEntity {
        @SchemaProperty
        public String title;

        @SchemaProperty
        public int count;

        @SchemaProperty
        public boolean active;

        @SchemaProperty
        public double score;
    }


    static class NestedEntity {
        @SchemaProperty
        public String name;

        @SchemaProperty
        public SimpleEntity child;
    }


    private final ClassSchemaGenerator generator = new ClassSchemaGenerator();

    @Test
    void rootHasCorrectNameAndType() {
        var doc = generator.generate(SimpleEntity.class);
        var root = (SimpleMapNode) doc.getRoot();

        // assertEquals("SimpleEntity", root.getString("name"));
        assertEquals("object", root.getString("type"));
    }

    @Test
    void generatesCorrectScalarProperties() {
        var doc = generator.generate(SimpleEntity.class);
        var root = (SimpleMapNode) doc.getRoot();
        var props = (SimpleMapNode) root.get("properties");

        assertEquals("string", ((SimpleMapNode) props.get("title")).getString("type"));
        assertEquals("integer", ((SimpleMapNode) props.get("count")).getString("type"));
        assertEquals("boolean", ((SimpleMapNode) props.get("active")).getString("type"));
        assertEquals("number", ((SimpleMapNode) props.get("score")).getString("type"));
    }

    @Test
    void generatesNestedObjectProperty() {
        var doc = generator.generate(NestedEntity.class);
        var root = (SimpleMapNode) doc.getRoot();
        var props = (SimpleMapNode) root.get("properties");

        var child = (SimpleMapNode) props.get("child");
        assertNotNull(child);

        // Nested objects become references
        assertEquals("#/$defs/SimpleEntity", child.getString("$ref"));
        // assertEquals("SimpleEntity", child.getString("target"));
    }


    // @Test
    // void schemaIsStableAcrossRuns() {
    //     var doc1 = generator.generate(SimpleEntity.class);
    //     var doc2 = generator.generate(SimpleEntity.class);

        // class NoopResolver implements SchemaResolver {
        //     @Override public StructuredDocument resolve(URI uri) {
        //          return null; // no external schemas in tests
        //     }
        // }

    //     var compiler = new SchemaCompiler();
    //     var schema1 = compiler.compile(doc1, URI.create("runtime://schema1"));
    //     var schema2 = compiler.compile(doc2, URI.create("runtime://schema2"));


    //     // Object plain1 = toPlain.toPlain(doc1.getRoot());
    //     // Object plain2 = toPlain.toPlain(doc2.getRoot());
    //     // assertEquals(plain1, plain2);

    //     // // assertEquals(doc1.toString(), doc2.toString());
    //     // assertEquals(doc1.getRoot(), doc2.getRoot());

    // }

    @Test
    void schemaIsStableAcrossRuns() {
        var doc1 = generator.generate(SimpleEntity.class);
        var doc2 = generator.generate(SimpleEntity.class);

        CascaraSchemaResolver resolver = new CascaraSchemaResolver();
        CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(resolver);


        var schema1 = compiler.compile(doc1, URI.create("runtime://schema1"));
        var schema2 = compiler.compile(doc2, URI.create("runtime://schema2"));

        var toPlain = new SchemaNodeToPlain();
        var plain1 = toPlain.toPlain(schema1.getRoot());
        var plain2 = toPlain.toPlain(schema2.getRoot());

        assertEquals(plain1, plain2);
    }

    //
    //
    //

    // class NoopResolver implements SchemaResolver {
    //     @Override
    //     public AstNode resolve(String ref, SchemaNode relativeTo) {
    //         return null; // no external schemas in tests
    //     }

    //     @Override
    //     public AstNode resolveInternal(String fragment, AstNode root) {
    //         return null; // no external schemas in tests
    //     }
    // }

    @Test
    void scalarFieldsGenerateScalarTypes() {
        class Simple {
            @SchemaProperty public String name;
            @SchemaProperty public int age;
            @SchemaProperty public boolean active;
        }

        var doc = generator.generate(Simple.class);
        var root = (SimpleMapNode) doc.getRoot();
        var props = (SimpleMapNode) root.get("properties");

        assertEquals("string", ((SimpleMapNode) props.get("name")).getString("type"));
        assertEquals("integer", ((SimpleMapNode) props.get("age")).getString("type"));
        assertEquals("boolean", ((SimpleMapNode) props.get("active")).getString("type"));
    }

    @Test
    void nestedObjectBecomesReference() {
        class Address {
            @SchemaProperty public String street;
        }
        class Person {
            @SchemaProperty public Address address;
        }

        var doc = generator.generate(Person.class);
        var root = (SimpleMapNode) doc.getRoot();
        var props = (SimpleMapNode) root.get("properties");
        var address = (SimpleMapNode) props.get("address");

        assertEquals("#/$defs/Address", address.getString("$ref"));
        // assertEquals("Address", address.getString("target"));
    }

    @Test
    void listOfObjectsBecomesArrayOfReferences() {
        class Tag {
            @SchemaProperty public String name;
        }
        class Entry {
            @SchemaProperty public List<Tag> tags;
        }

        var doc = generator.generate(Entry.class);
        var root = (SimpleMapNode) doc.getRoot();
        var props = (SimpleMapNode) root.get("properties");
        var tags = (SimpleMapNode) props.get("tags");

        assertEquals("array", tags.getString("type"));

        var items = (SimpleMapNode) tags.get("items");
        assertEquals("#/$defs/Tag", items.getString("$ref"));
        // assertEquals("Tag", items.getString("target"));
    }

    @Test
    void mixedFieldsProduceCorrectSchema() {
        class Address {
            @SchemaProperty public String street;
        }
        class Tag {
            @SchemaProperty public String name;
        }
        class Person {
            @SchemaProperty public String name;
            @SchemaProperty public Address address;
            @SchemaProperty public List<Tag> tags;
        }

        var doc = generator.generate(Person.class);
        var root = (SimpleMapNode) doc.getRoot();
        var props = (SimpleMapNode) root.get("properties");

        // scalar
        assertEquals("string", ((SimpleMapNode) props.get("name")).getString("type"));

        // reference
        var address = (SimpleMapNode) props.get("address");
        assertEquals("#/$defs/Address", address.getString("$ref"));
        // assertEquals("Address", address.getString("target"));

        // array of references
        var tags = (SimpleMapNode) props.get("tags");
        assertEquals("array", tags.getString("type"));
        var items = (SimpleMapNode) tags.get("items");
        assertEquals("#/$defs/Tag", items.getString("$ref"));
        // assertEquals("Tag", items.getString("target"));
    }

    @Test
    void nestedObjectsDoNotGeneratePropertiesBlock() {
        class Address {
            @SchemaProperty public String street;
        }
        class Person {
            @SchemaProperty public Address address;
        }

        var doc = generator.generate(Person.class);
        var props = (SimpleMapNode) ((SimpleMapNode) doc.getRoot()).get("properties");
        var address = (SimpleMapNode) props.get("address");

        assertFalse(address.containsKey("properties"));
    }
}
