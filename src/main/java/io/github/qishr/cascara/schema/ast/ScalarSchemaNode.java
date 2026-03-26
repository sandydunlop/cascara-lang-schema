package io.github.qishr.cascara.schema.ast;

import java.util.Collections;
import java.util.Map;

public class ScalarSchemaNode extends BaseSchemaNode {
    private boolean primaryKey = false;


    public ScalarSchemaNode(String name, SchemaType type) {
        super(name, type);
    }

    @Override
    public Map<String, SchemaNode> getProperties() {
        // Scalars never have child properties
        return Collections.emptyMap();
    }

    @Override
    public SchemaNode getItemSchema() {
        // Scalars are not collections
        return null;
    }

    public boolean isPrimaryKey() {
        return primaryKey;
    }

    public void setPrimaryKey(boolean primaryKey) {
        this.primaryKey = primaryKey;
    }

}