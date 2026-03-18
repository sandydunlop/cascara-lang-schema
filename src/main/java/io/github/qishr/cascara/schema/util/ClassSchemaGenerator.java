package io.github.qishr.cascara.schema.util;

import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.DataIgnore;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.simple.*;
import io.github.qishr.cascara.schema.annotation.SchemaField;
import io.github.qishr.cascara.schema.annotation.SchemaObject;
import io.github.qishr.cascara.schema.api.TypeAnalyzer;
import io.github.qishr.cascara.schema.constraint.FileConstraint;
import io.github.qishr.cascara.schema.constraint.Hidden;
import io.github.qishr.cascara.schema.constraint.ReadOnly;
import io.github.qishr.cascara.schema.constraint.StringConstraint;

import java.lang.reflect.*;
import java.util.*;

public final class ClassSchemaGenerator {

    private static final String SCHEMA_SERVICE_URI = "cascara://core/schema-service/";

    private static final String ARRAY = "array";
    private static final String BOOLEAN = "boolean";
    private static final String INTEGER = "integer";
    private static final String NUMBER = "number";
    private static final String OBJECT = "object";
    private static final String STRING = "string";

    private final Set<Class<?>> processingStack = new HashSet<>();
    private final Map<Class<?>, SimpleMapNode> definitions = new LinkedHashMap<>();
    private final Set<TypeAnalyzer> typeAnalyzers = new HashSet<>();

    private boolean multiClassDocument = false;
    private MapAstNode definitionsContainer;
    private String definitionsLocation = "#/definitions/";

    public void registerTypeAnalyzer(TypeAnalyzer ta) {
        typeAnalyzers.add(ta);
    }

    public SimpleDocument generate(Class<?> clazz) {
        return generate(null, null, clazz);
    }

    public SimpleDocument generate(MapAstNode parentDoc, Class<?> clazz) {
        return generate(parentDoc, null, clazz);
    }

    public SimpleDocument generate(MapAstNode<?,?> parentDoc, String fragment, Class<?> clazz) {
        processingStack.clear();
        definitions.clear();
        multiClassDocument = false;

        if (parentDoc != null) {
            multiClassDocument = true;

            // If no location is specified for storing class definitions, use the default
            if (fragment == null) {
                fragment = definitionsLocation;
            }

            // The caller must create the definitions container as we don't know
            // what concrete implementation it should be.
            if (SchemaUtils.resolveFragment(parentDoc, fragment) instanceof MapAstNode map) {
                definitionsContainer = map;
            } else {
                throw new SchemaException("Path does not resolve to an object", fragment);
            }
        }

        SimpleMapNode classRoot = generateClassRoot(clazz);

        if (multiClassDocument) {
            for (Map.Entry<Class<?>, SimpleMapNode> e : definitions.entrySet()) {
                String defName = e.getKey().getSimpleName();
                definitionsContainer.put(defName, e.getValue());
            }
            definitionsContainer.put(clazz.getSimpleName(), classRoot);
        } else {
            if (!definitions.isEmpty()) {
                SimpleMapNode defsNode = new SimpleMapNode();
                for (Map.Entry<Class<?>, SimpleMapNode> e : definitions.entrySet()) {
                    String defName = e.getKey().getSimpleName();
                    defsNode.put(defName, e.getValue());
                }
                classRoot.put("definitions", defsNode);
            }
        }
        return new SimpleDocument(classRoot);
    }


    // TODO: This is only called from a test
    public SimpleDocument generateCombined(Class<?>... classes) {
        // Reset state for a fresh combined schema
        processingStack.clear();
        definitions.clear();

        SimpleMapNode root = new SimpleMapNode();
        root.put("name", scalar("Combined"));
        root.put("type", scalar("object"));

        SimpleMapNode props = new SimpleMapNode();
        root.put("properties", props);

        for (Class<?> clazz : classes) {
            // Generate a full schema document for the class
            SimpleDocument doc = generate(clazz);
            SimpleMapNode entity = (SimpleMapNode) doc.getRoot();

            // Remove the "name" field so it doesn't conflict
            entity.remove("name");

            // Insert under properties using the class simple name
            props.put(clazz.getSimpleName(), entity);

            // Merge definitions from this schema into the combined schema
            if (entity.get("definitions") instanceof SimpleMapNode defs) {
                defs.getEntries().forEach(entry -> {
                    if (entry instanceof SimpleMapEntryNode e &&
                        e.getKey() instanceof SimpleScalarNode key &&
                        e.getValue() instanceof SimpleMapNode value) {

                        definitions.putIfAbsent(clazz, value);
                    }
                });
            }
        }

        // If we collected definitions, add them to the combined schema
        if (!definitions.isEmpty()) {
            SimpleMapNode defsNode = new SimpleMapNode();
            for (Map.Entry<Class<?>, SimpleMapNode> e : definitions.entrySet()) {
                defsNode.put(e.getKey().getSimpleName(), e.getValue());
            }
            root.put("definitions", defsNode);
        }

        return new SimpleDocument(root);
    }



    private SimpleMapNode generateClassRoot(Class<?> clazz) {
        SimpleMapNode root = new SimpleMapNode();
        root.put("name", scalar(clazz.getSimpleName()));
        fillObjectMetadata(clazz, root);
        root.put("type", scalar(OBJECT));

        SimpleMapNode properties = new SimpleMapNode();
        root.put("properties", properties);

        properties.put("id", createIdFieldNode());

        Object template = instantiate(clazz);
        for (Field field : getAllFields(clazz)) {
            if (shouldInclude(field)) {
                properties.put(resolveFieldName(field), createFieldNode(field, template));
            }
        }

        return root;
    }

    private SimpleMapNode createIdFieldNode() {
        SimpleMapNode node = new SimpleMapNode();
        node.put("type", scalar(INTEGER));
        node.put("readOnly", scalar(true));
        return node;
    }

    private void fillObjectMetadata(Class<?> clazz, SimpleMapNode root) {
        if (clazz.isAnnotationPresent(SchemaObject.class)) {
            SchemaObject obj = clazz.getAnnotation(SchemaObject.class);

            String title = obj.title().isEmpty()
                ? clazz.getSimpleName()
                : obj.title();

            root.put("title", scalar(title));

            if (!obj.description().isEmpty()) {
                root.put("description", scalar(obj.description()));
            }
        }
    }

    private boolean shouldInclude(Field field) {
        if (field.isAnnotationPresent(DataIgnore.class)) return false;
        return field.isAnnotationPresent(SchemaField.class);
    }

    private String resolveFieldName(Field field) {
        if (field.isAnnotationPresent(DataField.class)) {
            String key = field.getAnnotation(DataField.class).key();
            if (key != null && !key.isEmpty()) {
                return key;
            }
        }
        return field.getName();
    }

    private SimpleMapNode createFieldNode(Field field, Object template) {
        SimpleMapNode node = new SimpleMapNode();
        SchemaField sf = field.getAnnotation(SchemaField.class);
        node.put("title", scalar(sf.title()));

        if (!sf.description().isEmpty()) {
            node.put("description", scalar(sf.description()));
        }

        appendDefaultValue(node, field, template);

        Class<?> type = field.getType();

        applyTypeAnalysis(field, node);
        String analyzedType = node.getString("type");

        if (isScalarType(type) || (analyzedType != null &&
            !ARRAY.equals(analyzedType) && !OBJECT.equals(analyzedType))
        ) {
            fillTypeInfo(node, type, field);
        }
        else if (isList(field)) {
            Class<?> elementType = getListElementType(field);
            node.put("type", scalar(ARRAY));
            node.put("items", createItemsNode(elementType, field));
        }
        else if (isExternalEntityType(type)) {
            // External entity → external $ref
            applyExternalRef(node, type, field);
        }
        else {
            // Embedded/value object → internal definition + $ref
            applyInternalRef(node, type);
        }

        applyConstraints(node, field);
        return node;
    }

    private SimpleMapNode createItemsNode(Class<?> elementType, Field field) {
        SimpleMapNode items = new SimpleMapNode();

        if (isScalarType(elementType)) {
            fillTypeInfo(items, elementType, field);
        } else if (isExternalEntityType(elementType)) {
            applyExternalRef(items, elementType, field);
        } else {
            applyInternalRef(items, elementType);
        }

        return items;
    }

    private void applyTypeAnalysis(Field field, SimpleMapNode targetAst) {
        for (TypeAnalyzer ta : typeAnalyzers) {
            ta.analyze(field, targetAst);
            ta.analyze(field.getType(), targetAst);
        }
    }

    private void applyExternalRef(SimpleMapNode node, Class<?> target, Field field) {
        node.put("$ref", scalar(SCHEMA_SERVICE_URI + target.getName()));
    }

    private void applyInternalRef(SimpleMapNode node, Class<?> target) {
        ensureDefinition(target);
        node.put("$ref", scalar(definitionsLocation + target.getSimpleName()));
    }

    private void ensureDefinition(Class<?> clazz) {
        if (definitions.containsKey(clazz)) return;
        if (processingStack.contains(clazz)) return;

        processingStack.add(clazz);
        try {
            SimpleMapNode def = new SimpleMapNode();
            def.put("type", scalar(OBJECT));

            fillObjectMetadata(clazz, def);

            SimpleMapNode properties = new SimpleMapNode();
            def.put("properties", properties);

            properties.put("id", createIdFieldNode());

            Object template = instantiate(clazz);
            for (Field field : getAllFields(clazz)) {
                if (shouldInclude(field)) {
                    properties.put(resolveFieldName(field), createFieldNode(field, template));
                }
            }

            definitions.put(clazz, def);
        } finally {
            processingStack.remove(clazz);
        }
    }

    private void fillTypeInfo(SimpleMapNode node, Class<?> type, Field field) {
        if (type == boolean.class || type == Boolean.class) {
            node.put("type", scalar(BOOLEAN));
        } else if (type == int.class || type == Integer.class
            || type == long.class || type == Long.class) {
            node.put("type", scalar(INTEGER));
        } else if (type == double.class || type == Double.class
            || type == float.class || type == Float.class) {
            node.put("type", scalar(NUMBER));
        } else if (type == String.class || type.isEnum()) {
            node.put("type", scalar(STRING));
            if (type.isEnum()) {
                SimpleSequenceNode enumNode = new SimpleSequenceNode();
                for (Object ec : type.getEnumConstants()) {
                    enumNode.add(scalar(ec.toString()));
                }
                node.put("enum", enumNode);
            }
        } else if (type == java.time.LocalDateTime.class
            || type == java.time.Instant.class) {
            node.put("type", scalar(STRING));
            node.put("format", scalar("date-time"));
        }

        applyConstraints(node, field);
    }

    private void applyConstraints(SimpleMapNode node, Field field) {
        if (field.isAnnotationPresent(StringConstraint.class)) {
            StringConstraint sc = field.getAnnotation(StringConstraint.class);

            // TODO: These are custom extensions. They should perhaps be handled by a TypeAnalyzer
            if (!sc.provider().isEmpty()) node.put("x-provider", scalar(sc.provider()));
            if (!sc.parameter().isEmpty()) node.put("x-parameter", scalar(sc.parameter()));

            if (sc.options().length > 0) {
                SimpleSequenceNode enumNode = new SimpleSequenceNode();
                for (String opt : sc.options()) enumNode.add(scalar(opt));
                node.put("enum", enumNode);
            }
        }

        if (field.isAnnotationPresent(FileConstraint.class)) {
            FileConstraint fc = field.getAnnotation(FileConstraint.class);

            node.put("format", scalar("path"));
            node.put("absolute", scalar(fc.absolute()));

            if (fc.extensions().length > 0) {
                SimpleSequenceNode extNode = new SimpleSequenceNode();
                for (String ext : fc.extensions()) extNode.add(scalar(ext));
                node.put("extensions", extNode);
            }
        }

        if (field.isAnnotationPresent(ReadOnly.class)) {
            node.put("readOnly", scalar(true));
        }

        if (field.isAnnotationPresent(Hidden.class)) {
            node.put("x-hidden", scalar(true));
        }
    }

    private void appendDefaultValue(SimpleMapNode node, Field field, Object instance) {
        if (instance == null) return;
        try {
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value != null && !(value instanceof List)) {
                node.put("default", scalar(value));
            }
        } catch (IllegalAccessException ignored) {}
    }

    private Object instantiate(Class<?> clazz) {
        try {
            return clazz.getDeclaredConstructor().newInstance();
        } catch (Exception e) {
            return null;
        }
    }

    private List<Field> getAllFields(Class<?> clazz) {
        List<Field> fields = new ArrayList<>();
        Class<?> current = clazz;

        while (current != null && current != Object.class) {
            fields.addAll(Arrays.asList(current.getDeclaredFields()));
            current = current.getSuperclass();
        }

        return fields;
    }

    private SimpleScalarNode scalar(Object value) {
        return new SimpleScalarNode(value);
    }

    private boolean isScalarType(Class<?> type) {
        return type.isPrimitive()
            || type == Boolean.class
            || type == Integer.class
            || type == Long.class
            || type == Double.class
            || type == Float.class
            || type == String.class
            || type.isEnum()
            || type == java.time.LocalDateTime.class
            || type == java.time.Instant.class;
    }

    private boolean isList(Field field) {
        return List.class.isAssignableFrom(field.getType());
    }

    private Class<?> getListElementType(Field field) {
        Type generic = field.getGenericType();

        if (generic instanceof ParameterizedType pt) {
            Type arg = pt.getActualTypeArguments()[0];

            if (arg instanceof Class<?> cls) {
                return cls;
            }

            if (arg instanceof ParameterizedType p2 && p2.getRawType() instanceof Class<?> cls2) {
                return cls2;
            }
        }

        throw new IllegalStateException(
            "List field " + field.getName() + " must declare a concrete generic type"
        );
    }

    private boolean isExternalEntityType(Class<?> type) {
        if (multiClassDocument) {
            return false;
        }
        // Heuristic: entities with their own schema documents
        return type.isAnnotationPresent(SchemaObject.class);
    }
}
