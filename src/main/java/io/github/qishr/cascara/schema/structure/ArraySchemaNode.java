package io.github.qishr.cascara.schema.structure;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.common.lang.ast.SequenceAstNode;
import io.github.qishr.cascara.schema.SchemaType;
import io.github.qishr.cascara.schema.util.ValidationResult;

import java.util.*;

public class ArraySchemaNode extends BaseSchemaNode {
    private SchemaNode items; // This is our Item Template

    public ArraySchemaNode(SchemaNode metaSchema) {
        super(SchemaType.ARRAY, metaSchema);
    }

    public SchemaNode getItemSchema() {
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
            int i = 0;
            for (AstNode item : sequence.getChildren()) {
                String itemPath = path + "[" + i + "]";
                items.validate(item, itemPath, result);
                i++;
            }
        }
    }
}