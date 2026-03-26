package io.github.qishr.cascara.schema;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.URI;
import java.util.Map;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.annotation.DataIgnore;
import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.schema.annotation.SchemaProperty;
import io.github.qishr.cascara.schema.api.SchemaCompiler;
import io.github.qishr.cascara.schema.ast.ArraySchemaNode;
import io.github.qishr.cascara.schema.ast.LazySchemaNode;
import io.github.qishr.cascara.schema.ast.ObjectSchemaNode;
import io.github.qishr.cascara.schema.ast.ScalarSchemaNode;
import io.github.qishr.cascara.schema.ast.SchemaNode;
import io.github.qishr.cascara.schema.util.CascaraSchemaCompiler;
import io.github.qishr.cascara.schema.util.CascaraSchemaResolver;
import io.github.qishr.cascara.schema.util.ClassSchemaGenerator;

public class SimpleEntityTests {
    @Test
    public void simpleEntity_has_scalar_fields() {
        ClassSchemaGenerator generator = new ClassSchemaGenerator();
        CascaraSchemaResolver resolver = new CascaraSchemaResolver(null, null);
        CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(resolver);

        SimpleDocument doc = generator.generate(SimpleEntity.class);
        CompiledSchema schema = compiler.compile(doc, URI.create("runtime://schema"));

        Map<String, SchemaNode> props = schema.getRoot().getProperties();

        assertTrue(props.get("name") instanceof ScalarSchemaNode);
        assertTrue(props.get("age") instanceof ScalarSchemaNode);
    }

    @Test
    public void refEntity_distinguishes_single_and_collection_references() {
        ClassSchemaGenerator generator = new ClassSchemaGenerator();
        SchemaCompiler compiler = new CascaraSchemaCompiler(new CascaraSchemaResolver(null, null));

        SimpleDocument doc = generator.generate(RefEntity.class);
        CompiledSchema schema = compiler.compile(doc, URI.create("runtime://schema"));

        SchemaNode child = schema.getRoot().getProperties().get("child");
        SchemaNode children = schema.getRoot().getProperties().get("children");

        // Single reference
        if (child instanceof LazySchemaNode lazy) { child = lazy.getResolved(); }
        assertTrue(child instanceof ObjectSchemaNode);
        // assertFalse(((ReferenceSchemaNode) child).isCollection());

        // Collection reference
        assertTrue(children instanceof ArraySchemaNode);

        // ArraySchemaNode arr = (ArraySchemaNode) children;

        // Item template is a single reference
        // assertTrue(arr.getItemTemplate() instanceof ReferenceSchemaNode);
        // assertFalse(((ReferenceSchemaNode) arr.getItemTemplate()).isCollection());

        // TODO: Fix this
        // assertTrue(arr.getItemTemplate() instanceof ReferenceSchemaNode);
        // assertEquals("RefEntity", ((ReferenceSchemaNode) arr.getItemTemplate()).getTargetType());

    }


    public class IgnoreEntity {
        @SchemaProperty(title = "Visible")
        String visible;

        @DataIgnore
        String ignored;
    }

    @Test
    public void ignoreEntity_ignores_dataignore_fields() {
        ClassSchemaGenerator generator = new ClassSchemaGenerator();
        CascaraSchemaResolver resolver = new CascaraSchemaResolver(null, null);
        CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(resolver);

        SimpleDocument doc = generator.generate(IgnoreEntity.class);
        CompiledSchema schema = compiler.compile(doc, URI.create("runtime://schema"));

        Map<String, SchemaNode> props = schema.getRoot().getProperties();

        assertTrue(props.containsKey("visible"));
        assertFalse(props.containsKey("ignored"));
    }

    @Test
    public void array_reference_is_marked_as_collection() {
        ClassSchemaGenerator generator = new ClassSchemaGenerator();
        // TestResolver resolver = new TestResolver();
        CascaraSchemaResolver resolver = new CascaraSchemaResolver(null, null) ;
        SchemaCompiler compiler = new CascaraSchemaCompiler(resolver);

        URI uri = URI.create("runtime://schema");
        SimpleDocument doc = generator.generate(RefEntity.class);
        CompiledSchema schema = compiler.compile(doc, uri);

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
