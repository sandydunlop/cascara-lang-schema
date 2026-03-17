package io.github.qishr.cascara.schema.util;

import java.util.List;
import java.util.Map;

import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.common.lang.simple.SimpleSequenceNode;

public final class PlainToSchemaAst {

    public SimpleNode toAst(Object plain) {
        if (plain == null) {
            return new SimpleScalarNode("null");
        }

        if (plain instanceof String s) {
            return new SimpleScalarNode(s);
        }

        if (plain instanceof Number n) {
            return new SimpleScalarNode(n.toString());
        }

        if (plain instanceof Boolean b) {
            return new SimpleScalarNode(b.toString());
        }

        if (plain instanceof Map<?, ?> map) {
            SimpleMapNode node = new SimpleMapNode();
            for (var entry : map.entrySet()) {
                String key = entry.getKey().toString();
                node.put(key, toAst(entry.getValue()));
            }
            return node;
        }

        if (plain instanceof List<?> list) {
            SimpleSequenceNode seq = new SimpleSequenceNode();
            for (Object item : list) {
                seq.add(toAst(item));
            }
            return seq;
        }

        throw new IllegalArgumentException("Unsupported type: " + plain.getClass());
    }
}
