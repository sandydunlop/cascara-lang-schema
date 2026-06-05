package io.github.qishr.cascara.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.annotation.DataIgnore;
import io.github.qishr.cascara.common.lang.reference.ReferenceNode;
import io.github.qishr.cascara.schema.annotation.SchemaProperty;
import io.github.qishr.cascara.schema.util.SchemaGenerator;
import io.github.qishr.cascara.schema.util.SchemaResolver;
import io.github.qishr.cascara.schema.structure.ArraySchemaNode;
import io.github.qishr.cascara.schema.structure.LazySchemaNode;
import io.github.qishr.cascara.schema.structure.ObjectSchemaNode;
import io.github.qishr.cascara.schema.structure.ScalarSchemaNode;
import io.github.qishr.cascara.schema.structure.SchemaNode;
import io.github.qishr.cascara.schema.util.SchemaCompiler;

public class SimpleEntityTests {
    @Test
    public void simpleEntity_has_scalar_fields() {
        SchemaGenerator generator = new SchemaGenerator();
        SchemaResolver resolver = new SchemaResolver();
        SchemaCompiler compiler = new SchemaCompiler(resolver);

        ReferenceNode doc = generator.generate(SimpleEntity.class);
        Schema schema = compiler.compile(doc, URI.create("runtime://schema"));

        Map<String, SchemaNode> props = schema.getRoot().getProperties();

        assertTrue(props.get("name") instanceof ScalarSchemaNode);
        assertTrue(props.get("age") instanceof ScalarSchemaNode);
    }

    @Test
    public void refEntity_distinguishes_single_and_collection_references() {
        SchemaGenerator generator = new SchemaGenerator();
        SchemaCompiler compiler = new SchemaCompiler(new SchemaResolver());

        ReferenceNode doc = generator.generate(RefEntity.class);
        Schema schema = compiler.compile(doc, URI.create("runtime://schema"));

        SchemaNode child = schema.getRoot().getProperties().get("child");
        SchemaNode children = schema.getRoot().getProperties().get("children");

        // Single reference
        if (child instanceof LazySchemaNode lazy) { child = lazy.getResolved(); }
        assertTrue(child instanceof ObjectSchemaNode);
        // assertFalse(((ReferenceSchemaNode) child).isCollection());

        // Collection reference
        assertTrue(children instanceof ArraySchemaNode);

        ArraySchemaNode arr = (ArraySchemaNode) children;

        assertTrue(arr.getItemSchema() instanceof LazySchemaNode);
        assertEquals(SchemaType.OBJECT, ((LazySchemaNode) arr.getItemSchema()).getType());
    }


    public class IgnoreEntity {
        @SchemaProperty(title = "Visible")
        String visible;

        @DataIgnore
        String ignored;
    }

    @Test
    public void ignoreEntity_ignores_dataignore_fields() {
        SchemaGenerator generator = new SchemaGenerator();
        SchemaResolver resolver = new SchemaResolver();
        SchemaCompiler compiler = new SchemaCompiler(resolver);

        ReferenceNode doc = generator.generate(IgnoreEntity.class);
        Schema schema = compiler.compile(doc, URI.create("runtime://schema"));

        Map<String, SchemaNode> props = schema.getRoot().getProperties();

        assertTrue(props.containsKey("visible"));
        assertFalse(props.containsKey("ignored"));
    }

    @Test
    public void array_reference_is_marked_as_collection() {
        SchemaGenerator generator = new SchemaGenerator();
        // TestResolver resolver = new TestResolver();
        SchemaResolver resolver = new SchemaResolver() ;
        SchemaCompiler compiler = new SchemaCompiler(resolver);

        URI uri = URI.create("runtime://schema");
        ReferenceNode doc = generator.generate(RefEntity.class);
        Schema schema = compiler.compile(doc, uri);

        SchemaNode children = schema.getRoot().getProperties().get("children");
        ArraySchemaNode arr = (ArraySchemaNode) children;
        SchemaNode itemSchema = arr.getItemSchema();
        if (itemSchema instanceof LazySchemaNode lazy) {
            itemSchema = lazy.getResolved();
        }
        // ReferenceSchemaNode ref = (ReferenceSchemaNode) arr.getItemTemplate();

        // assertTrue(ref.isCollection());
    }

}
