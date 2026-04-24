package io.github.qishr.cascara.schema.util;

import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.DataIgnore;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.simple.*;
import io.github.qishr.cascara.schema.annotation.SchemaProperty;
import io.github.qishr.cascara.schema.SchemaException;
import io.github.qishr.cascara.schema.SchemaKeyword;
import io.github.qishr.cascara.schema.annotation.ContentMediaType;
import io.github.qishr.cascara.schema.annotation.SchemaDefinition;
import io.github.qishr.cascara.schema.api.TypeAnalyzer;
import io.github.qishr.cascara.schema.constraint.ReadOnly;
import io.github.qishr.cascara.schema.constraint.StringConstraint;
import io.github.qishr.cascara.schema.internal.SchemaUtils;

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
    private SimpleMapNode definitionsContainer;
    private String definitionsLocation = "#/" + SchemaKeyword.DEFS.string();

    public void registerTypeAnalyzer(TypeAnalyzer ta) {
        typeAnalyzers.add(ta);
    }

    public SimpleDocument generate(Object template) {
        return generate(null, null, null, template);
    }

    public SimpleDocument generate(Class<?> clazz) {
        return generate(null, null, clazz, null);
    }

    public SimpleDocument generate(SimpleMapNode parentDoc, Class<?> clazz) {
        return generate(parentDoc, null, clazz, null);
    }

    public SimpleDocument generate(MapAstNode<?,?> parentDoc, String fragment, Class<?> clazz) {
        return generate(parentDoc, fragment, clazz, null);
    }

    public SimpleDocument generate(MapAstNode<?,?> parentDoc, String fragment, Class<?> clazz, Object template) {
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
            if (SchemaUtils.resolveFragment(parentDoc, fragment) instanceof SimpleMapNode map) {
                definitionsContainer = map;
            } else {
                throw new SchemaException("Path does not resolve to an object", fragment);
            }
        }

        SimpleMapNode classRoot = generateClassRoot(clazz, template);

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
                classRoot.put(SchemaKeyword.DEFS.string(), defsNode);
            }
        }
        return new SimpleDocument(classRoot);
    }

    private SimpleMapNode generateClassRoot(Class<?> clazz, Object template) {
        if (clazz == null) {
            clazz = template.getClass();
        }
        SimpleMapNode root = new SimpleMapNode();
        root.put("name", scalar(clazz.getSimpleName()));
        fillObjectMetadata(clazz, root);
        root.put("type", scalar(OBJECT));

        SimpleMapNode properties = new SimpleMapNode();
        root.put("properties", properties);

        properties.put("id", createIdFieldNode());

        if (template == null) {
            template = instantiate(clazz);
        }
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
        if (clazz.isAnnotationPresent(SchemaDefinition.class)) {
            SchemaDefinition definition = clazz.getAnnotation(SchemaDefinition.class);

            String title = definition.title().isEmpty()
                ? clazz.getSimpleName()
                : definition.title();

            root.put("title", scalar(title));

            if (!definition.description().isEmpty()) {
                root.put("description", scalar(definition.description()));
            }

            if (clazz.isAnnotationPresent(ContentMediaType.class)) {
                ContentMediaType mediaType = clazz.getAnnotation(ContentMediaType.class);
                root.put(SchemaKeyword.CONTENT_MEDIA_TYPE.string(), scalar(mediaType.value()));
            }

            // TODO: TypeAnalyzers
        }
    }

    private boolean shouldInclude(Field field) {
        if (field.isAnnotationPresent(DataIgnore.class)) return false;
        return field.isAnnotationPresent(SchemaProperty.class);
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
        SchemaProperty sf = field.getAnnotation(SchemaProperty.class);
        node.put("title", scalar(sf.title()));

        if (!sf.description().isEmpty()) {
            node.put("description", scalar(sf.description()));
        }

        if (field.isAnnotationPresent(ContentMediaType.class)) {
            ContentMediaType mediaType = field.getAnnotation(ContentMediaType.class);
            node.put(SchemaKeyword.CONTENT_MEDIA_TYPE.string(), scalar(mediaType.value()));
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
        CascaraSchemaUri schemaUri = new CascaraSchemaUri(target);
        String schemaUriString = schemaUri.toUri().toString();
        // node.put("$ref", scalar(SCHEMA_SERVICE_URI + target.getName()));
        node.put("$ref", scalar(schemaUriString));
    }

    private void applyInternalRef(SimpleMapNode node, Class<?> target) {
        ensureDefinition(target);
        node.put("$ref", scalar(definitionsLocation + "/" + target.getSimpleName()));
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

            if (sc.options().length > 0) {
                SimpleSequenceNode enumNode = new SimpleSequenceNode();
                for (String opt : sc.options()) enumNode.add(scalar(opt));
                node.put("enum", enumNode);
            }
        }
        if (field.isAnnotationPresent(ReadOnly.class)) {
            node.put("readOnly", scalar(true));
        }
        // TODO: Range, number, enum, etc
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
        return type.isAnnotationPresent(SchemaDefinition.class);
    }
}
