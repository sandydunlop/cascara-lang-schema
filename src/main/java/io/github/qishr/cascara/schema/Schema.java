package io.github.qishr.cascara.schema;

import java.net.URI;
import java.util.Collection;

import io.github.qishr.cascara.schema.structure.SchemaNode;

public interface Schema {

    Collection<SchemaNode> getProperties();
    Collection<SchemaNode> getDefinitions();
    SchemaNode getRoot();
    URI getOriginUri();
    URI getId();
    URI getSchemaUri();
    SchemaNode getDefinition(String name);
    SchemaNode getProperty(String name);

}
