package io.github.qishr.cascara.schema;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.annotation.DataIgnore;
import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.schema.annotation.SchemaProperty;
import io.github.qishr.cascara.schema.internal.CascaraSchemaCompiler;
import io.github.qishr.cascara.schema.internal.CascaraSchemaGenerator;
import io.github.qishr.cascara.schema.internal.CascaraSchemaResolver;
import io.github.qishr.cascara.schema.structure.ArraySchemaNode;
import io.github.qishr.cascara.schema.structure.LazySchemaNode;
import io.github.qishr.cascara.schema.structure.ObjectSchemaNode;
import io.github.qishr.cascara.schema.structure.ScalarSchemaNode;
import io.github.qishr.cascara.schema.structure.SchemaNode;
import io.github.qishr.cascara.schema.util.SchemaCompiler;

public class SimpleEntityTests {
    @Test
    public void simpleEntity_has_scalar_fields() {
        CascaraSchemaGenerator generator = new CascaraSchemaGenerator();
        CascaraSchemaResolver resolver = new CascaraSchemaResolver();
        CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(resolver);

        SimpleDocument doc = generator.generate(SimpleEntity.class);
        Schema schema = compiler.compile(doc, URI.create("runtime://schema"));

        Map<String, SchemaNode> props = schema.getRoot().getProperties();

        assertTrue(props.get("name") instanceof ScalarSchemaNode);
        assertTrue(props.get("age") instanceof ScalarSchemaNode);
    }

    @Test
    public void refEntity_distinguishes_single_and_collection_references() {
        CascaraSchemaGenerator generator = new CascaraSchemaGenerator();
        SchemaCompiler compiler = new CascaraSchemaCompiler(new CascaraSchemaResolver());

        SimpleDocument doc = generator.generate(RefEntity.class);
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
        CascaraSchemaGenerator generator = new CascaraSchemaGenerator();
        CascaraSchemaResolver resolver = new CascaraSchemaResolver();
        CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(resolver);

        SimpleDocument doc = generator.generate(IgnoreEntity.class);
        Schema schema = compiler.compile(doc, URI.create("runtime://schema"));

        Map<String, SchemaNode> props = schema.getRoot().getProperties();

        assertTrue(props.containsKey("visible"));
        assertFalse(props.containsKey("ignored"));
    }

    @Test
    public void array_reference_is_marked_as_collection() {
        CascaraSchemaGenerator generator = new CascaraSchemaGenerator();
        // TestResolver resolver = new TestResolver();
        CascaraSchemaResolver resolver = new CascaraSchemaResolver() ;
        SchemaCompiler compiler = new CascaraSchemaCompiler(resolver);

        URI uri = URI.create("runtime://schema");
        SimpleDocument doc = generator.generate(RefEntity.class);
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
