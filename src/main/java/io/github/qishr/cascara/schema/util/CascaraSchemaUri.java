package io.github.qishr.cascara.schema.util;

import java.net.URI;
import java.util.LinkedList;
import java.util.Queue;

import io.github.qishr.cascara.common.content.ResourceContent;
import io.github.qishr.cascara.schema.SchemaException;

public class CascaraSchemaUri {

    public static enum Lifecycle {
        DYNAMIC,
        DRAFT,
        RESOURCE
    }

    private final URI uri;
    private final Lifecycle lifecycle;
    private final String moduleName;
    private final String schemaName;
    private final String version;

    private CascaraSchemaUri(URI uri, Lifecycle lifecycle, String moduleName, String schemaName, String version) {
        this.uri = uri;
        this.lifecycle = lifecycle;
        this.moduleName = moduleName;
        this.schemaName = schemaName;
        this.version = version;
    }

    public static CascaraSchemaUri of(URI uri) throws SchemaException {
        if (!uri.getHost().equalsIgnoreCase("core")) {
            throw new SchemaException("Not a valid schema URI: " + uri.toString(), "", uri);
        }

        Queue<String> segmentQueue = new LinkedList<>();
        String[] segments = uri.getPath().split("/");
        for (String s : segments) {
            // TODO: Is this right? Update DocumentService to match
            // segmentQueue.add(URLDecoder.decode(s, StandardCharsets.UTF_8));
            segmentQueue.add(s);
        }

        segmentQueue.poll(); // Remove the empty one

        if (!segmentQueue.poll().equalsIgnoreCase("schema-service")) {
            throw new SchemaException("Not a valid schema URI: " + uri.toString(), "", uri);
        }

        Lifecycle lifecycle;
        String lifecycleString = segmentQueue.poll();

        if (lifecycleString == null) {
            throw new SchemaException("Not a valid schema URI: " + uri.toString(), "", uri);
        }
        else if (lifecycleString.equals("dynamic")) {
            lifecycle = Lifecycle.DYNAMIC;
            // TODO: Generate from java class
            throw new SchemaException("Unimplemented: dynamic lifecycle", "", uri);
        }
        else if (lifecycleString.equals("draft")) {
            lifecycle = Lifecycle.DRAFT;
            // The "Latest" Alias: <module-name>/<schema-name>
            // TODO: This is called "resource" in the spec. Should it be renamed to "latest" or "current"?
        }
        else if (lifecycleString.equals("resource")) {
            lifecycle = Lifecycle.RESOURCE;
            // The "Latest" Alias: <module-name>/<schema-name>
            // TODO: This is called "resource" in the spec. Should it be renamed to "latest" or "current"?
        } else {
            throw new IllegalArgumentException("Unrecognized schema lifecycle: " + lifecycleString);
        }

        String moduleName = segmentQueue.poll();
        if (moduleName == null) {
            throw new IllegalArgumentException("Missing module name");
        }

        String schemaName = segmentQueue.poll();
        if (schemaName == null) {
            throw new IllegalArgumentException("Missing schema name");
        }

        String version = null;
        if (lifecycle == Lifecycle.DRAFT) {
            // Versioned Disk Assets: <module-name>/<schema-name>/<version>
            version = segmentQueue.poll();
        }

        return new CascaraSchemaUri(uri, lifecycle, moduleName, schemaName, version);

        // else {
        //     try {
        //         ResourceContent rc = schemaStore.get(lifecycleString, segmentQueue);
        //         return rc;
        //     } catch (Exception e) {
        //         throw new SchemaException(e.getMessage(), e, uri);
        //     }
        // }

    }

    public URI getUri() { return uri; }
    public Lifecycle getLifecycle() { return lifecycle; }
    public String getModuleName() { return moduleName; }
    public String getSchemaName() { return schemaName; }
    public String getVersion() { return version; }
}
