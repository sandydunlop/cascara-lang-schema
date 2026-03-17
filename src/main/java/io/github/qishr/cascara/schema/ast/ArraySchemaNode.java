package io.github.qishr.cascara.schema.ast;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.schema.util.ValidationResult;

import java.util.*;

public class ArraySchemaNode extends BaseSchemaNode {
    private SchemaNode items; // This is our Item Template

    public ArraySchemaNode(String name) {
        super(name, SchemaType.ARRAY);
    }

    public SchemaNode getItemTemplate() {
        return items;
    }

    public void setItemTemplate(SchemaNode items) {
        this.items = items;
    }

    @Override
    public Map<String, SchemaNode> getProperties() {
        return Collections.emptyMap();
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        super.validate(node, path, result);

        if (node instanceof SequenceAstNode sequence && items != null) {
            List<? extends AstNode> elements = sequence.getChildren();
            for (int i = 0; i < elements.size(); i++) {
                String itemPath = path + "[" + i + "]";
                items.validate(elements.get(i), itemPath, result);
            }
        }
    }
}