package io.github.qishr.cascara.schema.util;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import io.github.qishr.cascara.schema.ast.SchemaNode;

public final class SchemaDiffDiagnostic {

    public static List<String> diff(SchemaNode a, SchemaNode b) {
        List<String> out = new ArrayList<>();
        diffNode(a, b, "", true, out);
        return out;
    }

    private static void diffNode(SchemaNode a, SchemaNode b, String path, boolean isRoot, List<String> out) {
        if (a == null && b == null) return;
        if (a == null) {
            out.add(path + ": missing in old schema");
            return;
        }
        if (b == null) {
            out.add(path + ": missing in new schema");
            return;
        }

        if (a.getType() != b.getType()) {
            out.add(path + "/type: " + a.getType() + " → " + b.getType());
        }

        if (!Objects.equals(a.getTitle(), b.getTitle())) {
            out.add(path + "/title: " + a.getTitle() + " → " + b.getTitle());
        }

        if (!Objects.equals(a.getDescription(), b.getDescription())) {
            out.add(path + "/description: " + a.getDescription() + " → " + b.getDescription());
        }

        if (!Objects.equals(a.getDefaultValue(), b.getDefaultValue())) {
            out.add(path + "/default: " + a.getDefaultValue() + " → " + b.getDefaultValue());
        }

        if (a.isReadOnly() != b.isReadOnly()) {
            out.add(path + "/readOnly: " + a.isReadOnly() + " → " + b.isReadOnly());
        }

        if (a.isRef() != b.isRef()) {
            out.add(path + "/$ref: " + a.isRef() + " → " + b.isRef());
        } else if (a.isRef() && !Objects.equals(a.getRef(), b.getRef())) {
            out.add(path + "/$ref: " + a.getRef() + " → " + b.getRef());
        }

        // Refs are leaves: don't descend into properties/definitions/items
        if (a.isRef() || b.isRef()) {
            return;
        }

        switch (a.getType()) {
            case OBJECT -> diffObject(a, b, path, isRoot, out);
            case ARRAY -> diffArray(a, b, path, out);
            default -> {}
        }
    }

    private static void diffObject(SchemaNode a, SchemaNode b, String path, boolean isRoot, List<String> out) {
        Map<String, SchemaNode> pa = a.getProperties();
        Map<String, SchemaNode> pb = b.getProperties();

        for (String key : pa.keySet()) {
            if (!pb.containsKey(key)) {
                out.add(path + "/properties/" + key + ": removed");
            }
        }

        for (String key : pb.keySet()) {
            if (!pa.containsKey(key)) {
                out.add(path + "/properties/" + key + ": added");
            }
        }

        for (String key : pa.keySet()) {
            if (pb.containsKey(key)) {
                diffNode(pa.get(key), pb.get(key), path + "/properties/" + key, false, out);
            }
        }

        // definitions are global; only diff them at the root
        if (!isRoot) {
            return;
        }

        Map<String, SchemaNode> da = a.getDefinitions();
        Map<String, SchemaNode> db = b.getDefinitions();

        for (String key : da.keySet()) {
            if (!db.containsKey(key)) {
                out.add(path + "/definitions/" + key + ": removed");
            }
        }

        for (String key : db.keySet()) {
            if (!da.containsKey(key)) {
                out.add(path + "/definitions/" + key + ": added");
            }
        }

        for (String key : da.keySet()) {
            if (db.containsKey(key)) {
                diffNode(da.get(key), db.get(key), path + "/definitions/" + key, false, out);
            }
        }
    }

    private static void diffArray(SchemaNode a, SchemaNode b, String path, List<String> out) {
        diffNode(a.getItemTemplate(), b.getItemTemplate(), path + "/items", false, out);
    }
}
