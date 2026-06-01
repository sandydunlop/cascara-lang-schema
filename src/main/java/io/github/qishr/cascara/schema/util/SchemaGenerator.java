package io.github.qishr.cascara.schema.util;

import java.lang.reflect.Field;
import java.lang.reflect.ParameterizedType;
import java.lang.reflect.Type;
import java.net.URI;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

import io.github.qishr.cascara.common.lang.annotation.DataField;
import io.github.qishr.cascara.common.lang.annotation.DataIgnore;
import io.github.qishr.cascara.common.lang.ast.MapAstNode;
import io.github.qishr.cascara.common.lang.simple.SimpleDocument;
import io.github.qishr.cascara.common.lang.simple.SimpleMapNode;
import io.github.qishr.cascara.common.lang.simple.SimpleScalarNode;
import io.github.qishr.cascara.common.lang.simple.SimpleSequenceNode;
import io.github.qishr.cascara.common.service.CapabilityQueries;
import io.github.qishr.cascara.common.service.ServiceProviderLayer;
import io.github.qishr.cascara.common.service.ServiceProviderMetadata;
import io.github.qishr.cascara.common.type.TypeDescriptor;
import io.github.qishr.cascara.schema.SchemaException;
import io.github.qishr.cascara.schema.SchemaKeyword;
import io.github.qishr.cascara.schema.SchemaType;
import io.github.qishr.cascara.schema.annotation.ContentMediaType;
import io.github.qishr.cascara.schema.annotation.SchemaDefinition;
import io.github.qishr.cascara.schema.annotation.SchemaProperty;
import io.github.qishr.cascara.schema.constraint.NumberConstraint;
import io.github.qishr.cascara.schema.constraint.ReadOnly;
import io.github.qishr.cascara.schema.constraint.StringConstraint;
import io.github.qishr.cascara.schema.internal.SchemaUtils;

public final class SchemaGenerator {

    private static final String OBJECT_PROPERTY_CLASS = "javafx.beans.property.ObjectProperty";

    private final Set<Class<?>> processingStack = new HashSet<>();
    private final Map<Class<?>, SimpleMapNode> definitions = new LinkedHashMap<>();
    private final Set<TypeAnalyzer> typeAnalyzers = new HashSet<>();

    private boolean multiClassDocument = false;
    private SimpleMapNode definitionsContainer;
    private String definitionsLocation = "#/" + SchemaKeyword.DEFS.string();

    private URI originUri;

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

    // TODO: Perhaps fragment should be specified as a SchemaNode or AstNode?
    public SimpleDocument generate(MapAstNode<?,?> parentDoc, String fragment, Class<?> clazz, Object template) {
        processingStack.clear();
        definitions.clear();
        multiClassDocument = false;

        if (parentDoc != null) {
            multiClassDocument = true;
            String id = parentDoc.getString(SchemaKeyword.ID.string());
            if (id != null) {
                originUri = URI.create(id);
            }

            // If no location is specified for storing class definitions, use the default
            if (fragment == null) {
                fragment = definitionsLocation;
            }

            // The caller must create the definitions container as we don't know
            // what concrete implementation it should be.
            if (SchemaUtils.resolveFragment(parentDoc, fragment) instanceof SimpleMapNode map) {
                definitionsContainer = map;
            } else {
                throw new SchemaException("Path does not resolve to an object", fragment, originUri);
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
        fillObjectMetadata(clazz, root);
        root.put(SchemaKeyword.TYPE.string(), scalar(SchemaType.OBJECT.string()));

        SimpleMapNode properties = new SimpleMapNode();
        root.put(SchemaKeyword.PROPERTIES.string(), properties);

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

    private void fillObjectMetadata(Class<?> clazz, SimpleMapNode root) {
        if (clazz.isAnnotationPresent(SchemaDefinition.class)) {
            SchemaDefinition definition = clazz.getAnnotation(SchemaDefinition.class);

            String title = definition.title().isEmpty()
                ? clazz.getSimpleName()
                : definition.title();

            root.put(SchemaKeyword.TITLE.string(), title);

            if (!definition.description().isEmpty()) {
                root.put(SchemaKeyword.DESCRIPTION.string(), definition.description());
            } else {
                root.put(SchemaKeyword.DESCRIPTION.string(), clazz.getTypeName());
            }

            if (clazz.isAnnotationPresent(ContentMediaType.class)) {
                ContentMediaType mediaType = clazz.getAnnotation(ContentMediaType.class);
                root.put(SchemaKeyword.CONTENT_MEDIA_TYPE.string(), mediaType.value());
            }

            applyTypeAnalysis(clazz, root);
        } else {
            root.put(SchemaKeyword.TITLE.string(), clazz.getSimpleName());
            root.put(SchemaKeyword.DESCRIPTION.string(), clazz.getTypeName());
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
        node.put(SchemaKeyword.TITLE.string(), scalar(sf.title()));

        if (!sf.description().isEmpty()) {
            node.put(SchemaKeyword.DESCRIPTION.string(), scalar(sf.description()));
        }

        if (field.isAnnotationPresent(ContentMediaType.class)) {
            ContentMediaType mediaType = field.getAnnotation(ContentMediaType.class);
            node.put(SchemaKeyword.CONTENT_MEDIA_TYPE.string(), scalar(mediaType.value()));
        }

        appendDefaultValue(node, field, template);

        Class<?> type = extractFieldType(field);

        applyTypeAnalysis(field, node);
        String analyzedType = node.getString(SchemaKeyword.TYPE.string());

        if (isStandardScalarType(type) || (analyzedType != null &&
            !SchemaType.ARRAY.string().equals(analyzedType) && !SchemaType.OBJECT.string().equals(analyzedType))
        ) {
            fillTypeInfo(node, type, field);
        }
        else if (isList(type)) {
            Class<?> elementType = getListElementType(field);
            node.put(SchemaKeyword.TYPE.string(), scalar(SchemaType.ARRAY.string()));
            node.put(SchemaKeyword.ITEMS.string(), createItemsNode(elementType, field));
        } else {
            // If there is a type descriptor, use it.
            // If there isn't, then use a $ref
            ServiceProviderLayer rootLayer = ServiceProviderLayer.getRootLayer();
            List<ServiceProviderMetadata> typeConverters = rootLayer.getProviders(
                TypeDescriptor.class,
                CapabilityQueries.allOf(
                    CapabilityQueries.hasExactValue("type", type.getName())
                )
            );

            if (typeConverters.isEmpty()) {
                if (isExternalEntityType(type)) {
                    // External entity -> external $ref
                    applyExternalRef(node, type, field);
                }
                else {
                    // Embedded/value object -> internal definition + $ref
                    applyInternalRef(node, type);
                }
            } else {
                TypeDescriptor converter = ServiceProviderLayer.loadProvider(
                    TypeDescriptor.class,
                    typeConverters.getFirst()
                );
                converter.toSchema(node);
            }
        }

        applyConstraints(node, field);
        return node;
    }

    /// If the field is a JavaFX ObjectProperty, use the raw type, otherwise use the field's declared type
    private Class<?> extractFieldType(Field field) {
        if (field.getGenericType() instanceof ParameterizedType paramaterizedType) {
            String typeName = paramaterizedType.getRawType().getTypeName();
            Type[] paramTypes = paramaterizedType.getActualTypeArguments();
            if (paramTypes.length == 1 && typeName.equals(OBJECT_PROPERTY_CLASS)) {
                Type paramType = paramTypes[0];
                if (paramType instanceof Class clazz) {
                    return clazz;
                }
            }
        }
        return field.getType();
    }

    private SimpleMapNode createItemsNode(Class<?> elementType, Field field) {
        SimpleMapNode items = new SimpleMapNode();

        if (isStandardScalarType(elementType)) {
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

    // TODO: This might not work with ObjectProperty fields
    private void applyTypeAnalysis(Class<?> clazz, SimpleMapNode targetAst) {
        for (TypeAnalyzer ta : typeAnalyzers) {
            ta.analyze(clazz, targetAst);
        }
    }

    private void applyExternalRef(SimpleMapNode node, Class<?> target, Field field) {
        CascaraSchemaUri schemaUri = new CascaraSchemaUri(target);
        String schemaUriString = schemaUri.toUri().toString();
        node.put(SchemaKeyword.REF.string(), scalar(schemaUriString));
    }

    private void applyInternalRef(SimpleMapNode node, Class<?> target) {
        ensureDefinition(target);
        node.put(SchemaKeyword.REF.string(), scalar(definitionsLocation + "/" + target.getSimpleName()));
    }

    private void ensureDefinition(Class<?> clazz) {
        if (definitions.containsKey(clazz)) return;
        if (processingStack.contains(clazz)) return;

        processingStack.add(clazz);
        try {
            SimpleMapNode def = new SimpleMapNode();
            def.put(SchemaKeyword.TYPE.string(), scalar(SchemaType.OBJECT.string()));

            fillObjectMetadata(clazz, def);

            SimpleMapNode properties = new SimpleMapNode();

            def.put(SchemaKeyword.PROPERTIES.string(), properties);

            // TODO: This probably shoudn't be here
            // properties.put(SchemaKeyword.ID.string(), createIdFieldNode());

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
            node.put(SchemaKeyword.TYPE.string(), scalar(SchemaType.BOOLEAN.string()));
        } else if (type == int.class || type == Integer.class
            || type == long.class || type == Long.class) {
            node.put(SchemaKeyword.TYPE.string(), scalar(SchemaType.INTEGER.string()));
        } else if (type == double.class || type == Double.class
            || type == float.class || type == Float.class) {
            node.put(SchemaKeyword.TYPE.string(), scalar(SchemaType.NUMBER.string()));
        } else if (type == String.class || type.isEnum()) {
            node.put(SchemaKeyword.TYPE.string(), scalar(SchemaType.STRING.string()));
            if (type.isEnum()) {
                SimpleSequenceNode enumNode = new SimpleSequenceNode();
                for (Object ec : type.getEnumConstants()) {
                    enumNode.add(scalar(ec.toString()));
                }
                node.put(SchemaKeyword.ENUM.string(), enumNode);
            }
        }

        applyConstraints(node, field);
    }

    private void applyConstraints(SimpleMapNode node, Field field) {
        if (field.isAnnotationPresent(StringConstraint.class)) {
            StringConstraint constraint = field.getAnnotation(StringConstraint.class);

            if (constraint.options().length > 0) {
                SimpleSequenceNode enumNode = new SimpleSequenceNode();
                for (String opt : constraint.options()) {
                    enumNode.add(scalar(opt));
                }
                node.put(SchemaKeyword.ENUM.string(), enumNode);
            }
            if (constraint.minLength() > -1) {
                node.put(SchemaKeyword.MIN_LENGTH.string(), scalar(constraint.minLength()));
            }
            if (constraint.maxLength() > -1) {
                node.put(SchemaKeyword.MAX_LENGTH.string(), scalar(constraint.maxLength()));
            }
            // TODO: pattern, regex rule
        }

        if (field.isAnnotationPresent(ReadOnly.class)) {
            node.put(SchemaKeyword.READ_ONLY.string(), scalar(true));
        }

        if (field.isAnnotationPresent(NumberConstraint.class)) {
            NumberConstraint constraint = field.getAnnotation(NumberConstraint.class);
            if (constraint.min() != Double.NEGATIVE_INFINITY) {
                node.put(SchemaKeyword.MINIMUM.string(), scalar(constraint.min()));
            }
            if (constraint.max() != Double.POSITIVE_INFINITY) {
                node.put(SchemaKeyword.MAXIMUM.string(), scalar(constraint.max()));
            }
        }
    }





    private void appendDefaultValue(SimpleMapNode node, Field field, Object instance) {
        if (instance == null) return;
        try {
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value != null && !(value instanceof List)) {
                node.put(SchemaKeyword.DEFAULT.string(), scalar(value));
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

    private boolean isStandardScalarType(Class<?> type) {
        return type.isPrimitive()
            || type == Boolean.class
            || type == Integer.class
            || type == Long.class
            || type == Double.class
            || type == Float.class
            || type == String.class
            || type.isEnum();
    }

    private boolean isList(Class<?> type) {
        return List.class.isAssignableFrom(type);
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
