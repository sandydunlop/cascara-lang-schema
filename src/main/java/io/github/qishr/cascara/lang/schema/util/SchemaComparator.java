package io.github.qishr.cascara.lang.schema.util;

import java.util.Map;
import java.util.Objects;

import io.github.qishr.cascara.lang.schema.ast.SchemaNode;

public final class SchemaComparator {

    public static boolean equals(SchemaNode a, SchemaNode b) {
        if (a == b) return true;
        if (a == null || b == null) return false;

        if (a.getType() != b.getType()) return false;
        if (!Objects.equals(a.getTitle(), b.getTitle())) return false;
        if (!Objects.equals(a.getDescription(), b.getDescription())) return false;
        if (!Objects.equals(a.getDefaultValue(), b.getDefaultValue())) return false;
        if (a.isReadOnly() != b.isReadOnly()) return false;
        // if (a.isHidden() != b.isHidden()) return false;

        // TODO: compare custom hints

        // Compare validation rules (optional)
        if (!Objects.equals(a.getRules(), b.getRules())) return false;

        // Compare refs
        if (a.isRef() != b.isRef()) return false;
        if (a.isRef() && !Objects.equals(a.getRef(), b.getRef())) return false;

        return switch (a.getType()) {
            case OBJECT -> compareObjects(a, b);
            case ARRAY -> compareArrays(a, b);
            default -> true;
        };
    }

    private static boolean compareObjects(SchemaNode a, SchemaNode b) {
        Map<String, SchemaNode> pa = a.getProperties();
        Map<String, SchemaNode> pb = b.getProperties();

        if (!pa.keySet().equals(pb.keySet())) return false;

        for (String key : pa.keySet()) {
            if (!equals(pa.get(key), pb.get(key))) return false;
        }

        // Compare definitions
        Map<String, SchemaNode> da = a.getDefinitions();
        Map<String, SchemaNode> db = b.getDefinitions();

        if (!da.keySet().equals(db.keySet())) return false;

        for (String key : da.keySet()) {
            if (!equals(da.get(key), db.get(key))) return false;
        }

        return true;
    }

    private static boolean compareArrays(SchemaNode a, SchemaNode b) {
        return equals(a.getItemTemplate(), b.getItemTemplate());
    }
}
