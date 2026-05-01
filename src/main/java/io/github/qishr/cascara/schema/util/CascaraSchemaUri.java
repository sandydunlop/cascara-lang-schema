package io.github.qishr.cascara.schema.util;

import java.net.URI;
import java.util.LinkedList;
import java.util.Queue;

import io.github.qishr.cascara.schema.SchemaException;

public class CascaraSchemaUri {
    public static final String SCHEMA_SERVICE_URI = "cascara://core/schema-service";

    public static enum Lifecycle {
        DYNAMIC,
        DRAFT,
        RESOURCE
    }

    private final Lifecycle lifecycle;
    private final String moduleName;
    private final String schemaName;
    private final String version;

    public CascaraSchemaUri(Class<?> clazz) {
        this(Lifecycle.DYNAMIC, clazz.getModule().getName(), clazz.getName(), null);
    }

    public CascaraSchemaUri(String moduleName, String schemaName, String version) {
        this(Lifecycle.DRAFT, moduleName, schemaName, version);
    }

    public CascaraSchemaUri(String moduleName, String schemaName) {
        this(Lifecycle.RESOURCE, moduleName, schemaName, null);
    }

    public CascaraSchemaUri(String schemaName) {
        this(Lifecycle.DYNAMIC, "-", schemaName, null);
    }

    private CascaraSchemaUri(Lifecycle lifecycle, String moduleName, String schemaName, String version) {
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
            // Runtime Generation: dynamic/<module-name>/<schema-name>
            lifecycle = Lifecycle.DYNAMIC;
        }
        else if (lifecycleString.equals("draft")) {
            // Versioned Disk Assets: draft/<module-name>/<schema-name>/<version>
            lifecycle = Lifecycle.DRAFT;
        }
        else if (lifecycleString.equals("resource")) {
            // The "Latest" Alias: draft/<module-name>/<schema-name>
            lifecycle = Lifecycle.RESOURCE;
        } else {
            throw new SchemaException("Unrecognized schema lifecycle: " + lifecycleString, uri);
        }

        String moduleName = segmentQueue.poll();
        if (moduleName == null) {
            throw new SchemaException("Missing module name", uri);
        }

        String schemaName = segmentQueue.poll();
        if (schemaName == null) {
            throw new SchemaException("Missing schema name", uri);
        }

        String version = null;
        if (lifecycle == Lifecycle.DRAFT) {
            // Versioned Disk Assets: draft/<module-name>/<schema-name>/<version>
            version = segmentQueue.poll();
            if (version == null) {
                throw new SchemaException("Missing version", uri);
            }
        }

        return new CascaraSchemaUri(lifecycle, moduleName, schemaName, version);
    }

    public URI toUri() {
        String uriString = switch (lifecycle) {
            case DRAFT: yield String.format(
                "%s/%s/%s/%s/%s",
                SCHEMA_SERVICE_URI,
                "draft",
                moduleName, schemaName, version
            );
            case RESOURCE: yield String.format(
                "%s/%s/%s/%s",
                SCHEMA_SERVICE_URI,
                "resource",
                moduleName, schemaName
            );
            default: yield String.format(
                "%s/%s/%s/%s",
                SCHEMA_SERVICE_URI,
                "dynamic",
                moduleName, schemaName
            );
        };
        return URI.create(uriString);
    }

    public Lifecycle getLifecycle() { return lifecycle; }
    public String getModuleName() { return moduleName; }
    public String getSchemaName() { return schemaName; }
    public String getVersion() { return version; }
}
