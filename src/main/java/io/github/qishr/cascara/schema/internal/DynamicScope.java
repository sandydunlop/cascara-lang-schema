package io.github.qishr.cascara.schema.internal;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

public class DynamicScope {
    private final Map<String, URI> anchors = new HashMap<>();
    private final DynamicScope parent;

    public DynamicScope(DynamicScope parent) {
        this.parent = parent;
    }

    public void addAnchor(String name, URI uri) {
        anchors.put(name, uri);
    }

    public URI findAnchor(String name) {
        if (anchors.containsKey(name)) {
            return anchors.get(name);
        }
        return (parent != null) ? parent.findAnchor(name) : null;
    }
}
