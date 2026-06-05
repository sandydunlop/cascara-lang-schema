package io.github.qishr.cascara.schema.util;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import org.junit.jupiter.api.Test;

import io.github.qishr.cascara.common.lang.reference.ReferenceMapNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceScalarNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceSequenceNode;
import io.github.qishr.cascara.schema.Schema;
import io.github.qishr.cascara.schema.structure.ObjectSchemaNode;

public class SchemaCompilerTests {

    private SchemaResolver resolver = new SchemaResolver();
    private SchemaCompiler compiler = new SchemaCompiler(resolver);



    @Test
    void shouldCaptureUnevaluatedPropertiesAndTypedHints() {
        // 1. Setup 'parent' with x-tracked: true (Boolean)
        ReferenceMapNode parentProps = new ReferenceMapNode();
        parentProps.put("status", createScalarProperty("string", "x-tracked", true));

        ReferenceMapNode parentDef = new ReferenceMapNode();
        parentDef.put("type", new ReferenceScalarNode("object"));
        parentDef.put("properties", parentProps);

        // 2. Setup 'child' with allOf: [parent] and unevaluatedProperties: false
        ReferenceSequenceNode allOf = new ReferenceSequenceNode();
        ReferenceMapNode refNode = new ReferenceMapNode();
        refNode.put("$ref", new ReferenceScalarNode("#/definitions/parent"));
        allOf.add(refNode);

        ReferenceMapNode childDef = new ReferenceMapNode();
        childDef.put("type", new ReferenceScalarNode("object"));
        childDef.put("allOf", allOf);
        childDef.put("unevaluatedProperties", new ReferenceScalarNode(false));

        ReferenceMapNode defs = new ReferenceMapNode();
        defs.put("parent", parentDef);
        defs.put("child", childDef);

        ReferenceMapNode root = new ReferenceMapNode();
        root.put("$id", new ReferenceScalarNode("cascara://core/schema-service/dynamic/cascara.schema/compiler-unevaluated-test"));
        root.put("definitions", defs);

        Schema compiled = compiler.compile(root);

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
        ReferenceMapNode parentProps = new ReferenceMapNode();
        parentProps.put("base_field", createScalarProperty("string", "x-tracked", true));

        ReferenceMapNode parentDef = new ReferenceMapNode();
        parentDef.put("type", new ReferenceScalarNode("object"));
        parentDef.put("properties", parentProps);

        // 2. Create Child using allOf
        ReferenceSequenceNode allOf = new ReferenceSequenceNode();
        ReferenceMapNode refNode = new ReferenceMapNode();
        refNode.put("$ref", new ReferenceScalarNode("#/definitions/parent"));
        allOf.add(refNode);

        ReferenceMapNode childDef = new ReferenceMapNode();
        childDef.put("allOf", allOf);

        ReferenceMapNode defs = new ReferenceMapNode();
        defs.put("parent", parentDef);
        defs.put("child", childDef);

        ReferenceMapNode root = new ReferenceMapNode();
        root.put("$id", new ReferenceScalarNode("cascara://core/schema-service/dynamic/cascara.schema/compiler-flatten-test"));
        root.put("definitions", defs);

        Schema compiled = compiler.compile(root);

        ObjectSchemaNode childNode = (ObjectSchemaNode) compiled.getRoot()
                .getDefinition("child");

        assertTrue(childNode.getProperties().containsKey("base_field"));
        Object hint = childNode.getProperties().get("base_field").getExtension("x-tracked");
        assertTrue(hint instanceof Boolean);
    }

    @Test
    void shouldRespectAdditionalPropertiesFalse() {
        ReferenceMapNode root = new ReferenceMapNode();
        root.put("$id", new ReferenceScalarNode("cascara://core/schema-service/dynamic/cascara.schema/compiler-additional-properties-test"));
        root.put("type", new ReferenceScalarNode("object"));
        root.put("additionalProperties", new ReferenceScalarNode(false));

        Schema compiled = compiler.compile(root);
        ObjectSchemaNode rootNode = (ObjectSchemaNode) compiled.getRoot();

        assertFalse(rootNode.areAdditionalPropertiesAllowed());
    }

    private ReferenceMapNode createScalarProperty(String type, String hintKey, Object hintVal) {
        ReferenceMapNode prop = new ReferenceMapNode();
        prop.put("type", new ReferenceScalarNode(type));
        prop.put(hintKey, new ReferenceScalarNode(hintVal));
        return prop;
    }
}