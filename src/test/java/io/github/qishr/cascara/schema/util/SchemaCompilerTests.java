package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.common.lang.simple.SimpleSequenceNode;
import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.internal.CascaraSchemaCompiler;
import io.github.qishr.cascara.schema.internal.CascaraSchemaResolver;
import io.github.qishr.cascara.schema.internal.CompiledSchema;
import io.github.qishr.cascara.schema.structure.ObjectSchemaNode;

public class SchemaCompilerTests {

    private CascaraSchemaResolver resolver = new CascaraSchemaResolver();
    private CascaraSchemaCompiler compiler = new CascaraSchemaCompiler(resolver);



    @Test
    void shouldCaptureUnevaluatedPropertiesAndTypedHints() {
        // 1. Setup 'parent' with x-tracked: true (Boolean)
        SimpleMapNode parentProps = new SimpleMapNode();
        parentProps.put("status", createScalarProperty("string", "x-tracked", true));

        SimpleMapNode parentDef = new SimpleMapNode();
        parentDef.put("type", new SimpleScalarNode("object"));
        parentDef.put("properties", parentProps);

        // 2. Setup 'child' with allOf: [parent] and unevaluatedProperties: false
        SimpleSequenceNode allOf = new SimpleSequenceNode();
        SimpleMapNode refNode = new SimpleMapNode();
        refNode.put("$ref", new SimpleScalarNode("#/definitions/parent"));
        allOf.add(refNode);

        SimpleMapNode childDef = new SimpleMapNode();
        childDef.put("type", new SimpleScalarNode("object"));
        childDef.put("allOf", allOf);
        childDef.put("unevaluatedProperties", new SimpleScalarNode(false));

        SimpleMapNode defs = new SimpleMapNode();
        defs.put("parent", parentDef);
        defs.put("child", childDef);

        SimpleMapNode root = new SimpleMapNode();
        root.put("$id", new SimpleScalarNode("cascara://core/schema-service/dynamic/cascara.schema/compiler-unevaluated-test"));
        root.put("definitions", defs);

        Schema compiled = compiler.compile(new SimpleDocument(root));

        ObjectSchemaNode childNode = (ObjectSchemaNode) compiled.getRoot()
                .getDefinition("child");

        assertFalse(childNode.areUnevaluatedPropertiesAllowed());

        // Ensure flattening worked: status should be in child properties
        assertTrue(childNode.getProperties().containsKey("status"));

        Object hint = childNode.getProperties().get("status").getExtension("x-tracked");
        assertTrue(hint instanceof Boolean);
        assertEquals(true, hint);
    }

    @Test
    void shouldFlattenAllOfInheritance() {
        // 1. Create Parent
        SimpleMapNode parentProps = new SimpleMapNode();
        parentProps.put("base_field", createScalarProperty("string", "x-tracked", true));

        SimpleMapNode parentDef = new SimpleMapNode();
        parentDef.put("type", new SimpleScalarNode("object"));
        parentDef.put("properties", parentProps);

        // 2. Create Child using allOf
        SimpleSequenceNode allOf = new SimpleSequenceNode();
        SimpleMapNode refNode = new SimpleMapNode();
        refNode.put("$ref", new SimpleScalarNode("#/definitions/parent"));
        allOf.add(refNode);

        SimpleMapNode childDef = new SimpleMapNode();
        childDef.put("allOf", allOf);

        SimpleMapNode defs = new SimpleMapNode();
        defs.put("parent", parentDef);
        defs.put("child", childDef);

        SimpleMapNode root = new SimpleMapNode();
        root.put("$id", new SimpleScalarNode("cascara://core/schema-service/dynamic/cascara.schema/compiler-flatten-test"));
        root.put("definitions", defs);

        Schema compiled = compiler.compile(new SimpleDocument(root));

        ObjectSchemaNode childNode = (ObjectSchemaNode) compiled.getRoot()
                .getDefinition("child");

        assertTrue(childNode.getProperties().containsKey("base_field"));
        Object hint = childNode.getProperties().get("base_field").getExtension("x-tracked");
        assertTrue(hint instanceof Boolean);
    }

    @Test
    void shouldRespectAdditionalPropertiesFalse() {
        SimpleMapNode root = new SimpleMapNode();
        root.put("$id", new SimpleScalarNode("cascara://core/schema-service/dynamic/cascara.schema/compiler-additional-properties-test"));
        root.put("type", new SimpleScalarNode("object"));
        root.put("additionalProperties", new SimpleScalarNode(false));

        Schema compiled = compiler.compile(new SimpleDocument(root));
        ObjectSchemaNode rootNode = (ObjectSchemaNode) compiled.getRoot();

        assertFalse(rootNode.areAdditionalPropertiesAllowed());
    }

    private SimpleMapNode createScalarProperty(String type, String hintKey, Object hintVal) {
        SimpleMapNode prop = new SimpleMapNode();
        prop.put("type", new SimpleScalarNode(type));
        prop.put(hintKey, new SimpleScalarNode(hintVal));
        return prop;
    }
}