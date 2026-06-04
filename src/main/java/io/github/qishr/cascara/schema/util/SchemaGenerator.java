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
import io.github.qishr.cascara.common.lang.reference.ReferenceDocument;
import io.github.qishr.cascara.common.lang.reference.ReferenceMapNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceScalarNode;
import io.github.qishr.cascara.common.lang.reference.ReferenceSequenceNode;
import io.github.qishr.cascara.common.service.CapabilityQueries;
import io.github.qishr.cascara.common.service.ServiceProviderLayer;
import io.github.qishr.cascara.common.service.ServiceMetadata;
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
    private final Map<Class<?>, ReferenceMapNode> definitions = new LinkedHashMap<>();
    private final Set<TypeAnalyzer> typeAnalyzers = new HashSet<>();

    private boolean multiClassDocument = false;
    private ReferenceMapNode definitionsContainer;
    private String definitionsLocation = "#/" + SchemaKeyword.DEFS.asString();

    private URI originUri;

    public void registerTypeAnalyzer(TypeAnalyzer ta) {
        typeAnalyzers.add(ta);
    }

    public ReferenceDocument generate(Object template) {
        return generate(null, null, null, template);
    }

    public ReferenceDocument generate(Class<?> clazz) {
        return generate(null, null, clazz, null);
    }

    public ReferenceDocument generate(ReferenceMapNode parentDoc, Class<?> clazz) {
        return generate(parentDoc, null, clazz, null);
    }

    public ReferenceDocument generate(MapAstNode<?,?> parentDoc, String fragment, Class<?> clazz) {
        return generate(parentDoc, fragment, clazz, null);
    }

    // TODO: Perhaps fragment should be specified as a SchemaNode or AstNode?
    public ReferenceDocument generate(MapAstNode<?,?> parentDoc, String fragment, Class<?> clazz, Object template) {
        processingStack.clear();
        definitions.clear();
        multiClassDocument = false;

        if (parentDoc != null) {
            multiClassDocument = true;
            String id = parentDoc.getString(SchemaKeyword.ID.asString());
            if (id != null) {
                originUri = URI.create(id);
            }

            // If no location is specified for storing class definitions, use the default
            if (fragment == null) {
                fragment = definitionsLocation;
            }

            // The caller must create the definitions container as we don't know
            // what concrete implementation it should be.
            if (SchemaUtils.resolveFragment(parentDoc, fragment) instanceof ReferenceMapNode map) {
                definitionsContainer = map;
            } else {
                throw new SchemaException("Path does not resolve to an object", fragment, originUri);
            }
        }

        ReferenceMapNode classRoot = generateClassRoot(clazz, template);

        if (multiClassDocument) {
            for (Map.Entry<Class<?>, ReferenceMapNode> e : definitions.entrySet()) {
                String defName = e.getKey().getSimpleName();
                definitionsContainer.put(defName, e.getValue());
            }
            definitionsContainer.put(clazz.getSimpleName(), classRoot);
        } else {
            if (!definitions.isEmpty()) {
                ReferenceMapNode defsNode = new ReferenceMapNode();
                for (Map.Entry<Class<?>, ReferenceMapNode> e : definitions.entrySet()) {
                    String defName = e.getKey().getSimpleName();
                    defsNode.put(defName, e.getValue());
                }
                classRoot.put(SchemaKeyword.DEFS.asString(), defsNode);
            }
        }
        return new ReferenceDocument(classRoot);
    }

    private ReferenceMapNode generateClassRoot(Class<?> clazz, Object template) {
        if (clazz == null) {
            clazz = template.getClass();
        }
        ReferenceMapNode root = new ReferenceMapNode();
        fillObjectMetadata(clazz, root);
        root.put(SchemaKeyword.TYPE.asString(), scalar(SchemaType.OBJECT.asString()));

        ReferenceMapNode properties = new ReferenceMapNode();
        root.put(SchemaKeyword.PROPERTIES.asString(), properties);

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

    private void fillObjectMetadata(Class<?> clazz, ReferenceMapNode root) {
        if (clazz.isAnnotationPresent(SchemaDefinition.class)) {
            SchemaDefinition definition = clazz.getAnnotation(SchemaDefinition.class);

            String title = definition.title().isEmpty()
                ? clazz.getSimpleName()
                : definition.title();

            root.put(SchemaKeyword.TITLE.asString(), title);

            if (!definition.description().isEmpty()) {
                root.put(SchemaKeyword.DESCRIPTION.asString(), definition.description());
            } else {
                root.put(SchemaKeyword.DESCRIPTION.asString(), clazz.getTypeName());
            }

            if (clazz.isAnnotationPresent(ContentMediaType.class)) {
                ContentMediaType mediaType = clazz.getAnnotation(ContentMediaType.class);
                root.put(SchemaKeyword.CONTENT_MEDIA_TYPE.asString(), mediaType.value());
            }

            applyTypeAnalysis(clazz, root);
        } else {
            root.put(SchemaKeyword.TITLE.asString(), clazz.getSimpleName());
            root.put(SchemaKeyword.DESCRIPTION.asString(), clazz.getTypeName());
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

    private ReferenceMapNode createFieldNode(Field field, Object template) {
        ReferenceMapNode node = new ReferenceMapNode();
        SchemaProperty sf = field.getAnnotation(SchemaProperty.class);
        node.put(SchemaKeyword.TITLE.asString(), scalar(sf.title()));

        if (!sf.description().isEmpty()) {
            node.put(SchemaKeyword.DESCRIPTION.asString(), scalar(sf.description()));
        }

        if (field.isAnnotationPresent(ContentMediaType.class)) {
            ContentMediaType mediaType = field.getAnnotation(ContentMediaType.class);
            node.put(SchemaKeyword.CONTENT_MEDIA_TYPE.asString(), scalar(mediaType.value()));
        }

        appendDefaultValue(node, field, template);

        Class<?> type = extractFieldType(field);

        applyTypeAnalysis(field, node);
        String analyzedType = node.getString(SchemaKeyword.TYPE.asString());

        if (isStandardScalarType(type) || (analyzedType != null &&
            !SchemaType.ARRAY.asString().equals(analyzedType) && !SchemaType.OBJECT.asString().equals(analyzedType))
        ) {
            fillTypeInfo(node, type, field);
        }
        else if (isList(type)) {
            Class<?> elementType = getListElementType(field);
            node.put(SchemaKeyword.TYPE.asString(), scalar(SchemaType.ARRAY.asString()));
            node.put(SchemaKeyword.ITEMS.asString(), createItemsNode(elementType, field));
        } else {
            // If there is a type descriptor, use it.
            // If there isn't, then use a $ref
            ServiceProviderLayer rootLayer = ServiceProviderLayer.getRootLayer();
            List<ServiceMetadata> typeConverters = rootLayer.getProviders(
                TypeDescriptor.class,
                CapabilityQueries.allOf(
                    CapabilityQueries.hasExactValue("javaType", type.getName())
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

    private ReferenceMapNode createItemsNode(Class<?> elementType, Field field) {
        ReferenceMapNode items = new ReferenceMapNode();

        if (isStandardScalarType(elementType)) {
            fillTypeInfo(items, elementType, field);
        } else if (isExternalEntityType(elementType)) {
            applyExternalRef(items, elementType, field);
        } else {
            applyInternalRef(items, elementType);
        }

        return items;
    }

    private void applyTypeAnalysis(Field field, ReferenceMapNode targetAst) {
        for (TypeAnalyzer ta : typeAnalyzers) {
            ta.analyze(field, targetAst);
            ta.analyze(field.getType(), targetAst);
        }
    }

    // TODO: This might not work with ObjectProperty fields
    private void applyTypeAnalysis(Class<?> clazz, ReferenceMapNode targetAst) {
        for (TypeAnalyzer ta : typeAnalyzers) {
            ta.analyze(clazz, targetAst);
        }
    }

    private void applyExternalRef(ReferenceMapNode node, Class<?> target, Field field) {
        CascaraSchemaUri schemaUri = new CascaraSchemaUri(target);
        String schemaUriString = schemaUri.toUri().toString();
        node.put(SchemaKeyword.REF.asString(), scalar(schemaUriString));
    }

    private void applyInternalRef(ReferenceMapNode node, Class<?> target) {
        ensureDefinition(target);
        node.put(SchemaKeyword.REF.asString(), scalar(definitionsLocation + "/" + target.getSimpleName()));
    }

    private void ensureDefinition(Class<?> clazz) {
        if (definitions.containsKey(clazz)) return;
        if (processingStack.contains(clazz)) return;

        processingStack.add(clazz);
        try {
            ReferenceMapNode def = new ReferenceMapNode();
            def.put(SchemaKeyword.TYPE.asString(), scalar(SchemaType.OBJECT.asString()));

            fillObjectMetadata(clazz, def);

            ReferenceMapNode properties = new ReferenceMapNode();

            def.put(SchemaKeyword.PROPERTIES.asString(), properties);

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





    private void fillTypeInfo(ReferenceMapNode node, Class<?> type, Field field) {
        if (type == boolean.class || type == Boolean.class) {
            node.put(SchemaKeyword.TYPE.asString(), scalar(SchemaType.BOOLEAN.asString()));
        } else if (type == int.class || type == Integer.class
            || type == long.class || type == Long.class) {
            node.put(SchemaKeyword.TYPE.asString(), scalar(SchemaType.INTEGER.asString()));
        } else if (type == double.class || type == Double.class
            || type == float.class || type == Float.class) {
            node.put(SchemaKeyword.TYPE.asString(), scalar(SchemaType.NUMBER.asString()));
        } else if (type == String.class || type.isEnum()) {
            node.put(SchemaKeyword.TYPE.asString(), scalar(SchemaType.STRING.asString()));
            if (type.isEnum()) {
                ReferenceSequenceNode enumNode = new ReferenceSequenceNode();
                for (Object ec : type.getEnumConstants()) {
                    enumNode.add(scalar(ec.toString()));
                }
                node.put(SchemaKeyword.ENUM.asString(), enumNode);
            }
        }

        applyConstraints(node, field);
    }

    private void applyConstraints(ReferenceMapNode node, Field field) {
        if (field.isAnnotationPresent(StringConstraint.class)) {
            StringConstraint constraint = field.getAnnotation(StringConstraint.class);

            if (constraint.options().length > 0) {
                ReferenceSequenceNode enumNode = new ReferenceSequenceNode();
                for (String opt : constraint.options()) {
                    enumNode.add(scalar(opt));
                }
                node.put(SchemaKeyword.ENUM.asString(), enumNode);
            }
            if (constraint.minLength() > -1) {
                node.put(SchemaKeyword.MIN_LENGTH.asString(), scalar(constraint.minLength()));
            }
            if (constraint.maxLength() > -1) {
                node.put(SchemaKeyword.MAX_LENGTH.asString(), scalar(constraint.maxLength()));
            }
            // TODO: pattern, regex rule
        }

        if (field.isAnnotationPresent(ReadOnly.class)) {
            node.put(SchemaKeyword.READ_ONLY.asString(), scalar(true));
        }

        if (field.isAnnotationPresent(NumberConstraint.class)) {
            NumberConstraint constraint = field.getAnnotation(NumberConstraint.class);
            if (constraint.min() != Double.NEGATIVE_INFINITY) {
                node.put(SchemaKeyword.MINIMUM.asString(), scalar(constraint.min()));
            }
            if (constraint.max() != Double.POSITIVE_INFINITY) {
                node.put(SchemaKeyword.MAXIMUM.asString(), scalar(constraint.max()));
            }
        }
    }





    private void appendDefaultValue(ReferenceMapNode node, Field field, Object instance) {
        if (instance == null) return;
        try {
            field.setAccessible(true);
            Object value = field.get(instance);
            if (value != null && !(value instanceof List)) {
                node.put(SchemaKeyword.DEFAULT.asString(), scalar(value));
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

    private ReferenceScalarNode scalar(Object value) {
        return new ReferenceScalarNode(value);
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
