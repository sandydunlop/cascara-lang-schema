package io.github.qishr.cascara.schema.ast;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.schema.SchemaException;
import io.github.qishr.cascara.schema.SchemaType;
import io.github.qishr.cascara.schema.api.SchemaResolver;
import io.github.qishr.cascara.schema.rule.ValidationRule;
import io.github.qishr.cascara.schema.util.DynamicScope;
import io.github.qishr.cascara.schema.util.ValidationResult;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;


public class LazySchemaNode extends BaseSchemaNode {
    private final SchemaResolver resolver;
    private SchemaNode resolvedNode;
    private SchemaNode root;
    private AstNode initialAst;
    private final DynamicScope capturedScope;

    public LazySchemaNode(String ref, SchemaResolver resolver, SchemaNode root, URI originUri, AstNode originAst, DynamicScope scope, SchemaNode metaSchema) {
        super(null, metaSchema);
        this.ref = ref;
        this.resolver = resolver;
        this.root = root;
        this.originUri = originUri;
        this.initialAst = originAst;
        this.capturedScope = scope;
    }

    public void setRoot(SchemaNode root) {
        this.root = root;
    }

    public SchemaNode getRoot() {
        return root;
    }

    public SchemaNode peekResolved() {
        return this.resolvedNode;
    }

    public SchemaNode getResolved() throws SchemaException {
        if (resolvedNode == null) {

            // Ensure we always have a scope context
            DynamicScope scope = (capturedScope != null) ? capturedScope : new DynamicScope(null);

            // Use the scope-aware resolve method
            SchemaNode result = resolver.resolve(ref, this, scope);

            while (result instanceof LazySchemaNode lazy) {
                result = lazy.getResolved();
            }
            this.resolvedNode = result;

            if (result == null) {
                // Fallback for error reporting
                throw new SchemaException("Resolution failed", ref, getStartLine(), getStartColumn(), getOriginUri());
            }
        }
        return resolvedNode;
    }

    @Override
    public URI getOriginUri() {
        return originUri;
    }

    @Override
    public void validate(AstNode node, String path, ValidationResult result) {
        try {
            SchemaNode target = getResolved();
            if (target != null) {
                target.validate(node, path, result);
            } else {
                result.addError(path, "Target of reference '" + ref + "' is null",
                               node.getStartLine(), node.getStartColumn());
            }
        } catch (Exception e) {
            // Using your specific addError signature
            result.addError(path, "Broken schema reference: " + ref + " (" + e.getMessage() + ")",
                           node.getStartLine(), node.getStartColumn());
        }
    }

    public AstNode getInitialAst() {
        return initialAst;
    }

    @Override
    public AstNode getOriginAst() {
        // Once resolved, we delegate to follow the 'Proxy' requirement
        return (resolvedNode != null) ? resolvedNode.getOriginAst() : initialAst;
    }

    public SchemaType getType() {
        SchemaNode resolved = getResolved();
        if (resolved == null) {
            throw new IllegalStateException("Attempted to access type on LazySchemaNode before it was resolved.");
        }
        return resolved.getType();
    }

    @Override public SchemaNode getProperty(String key) { return getResolved().getProperty(key); }
    @Override public Map<String, SchemaNode> getProperties() { return getResolved().getProperties(); }
    @Override public SchemaNode getItemSchema() { return getResolved().getItemSchema(); }
    @Override public boolean isRef() { return true; }
    @Override public String getRef() { return this.ref; }
    @Override public List<ValidationRule> getRules() { return getResolved().getRules(); }
    @Override public String getTitle() { return getResolved().getTitle(); }
    @Override public String getDescription() { return getResolved().getDescription(); }
    @Override public Map<String, SchemaNode> getDefinitions() { return getResolved().getDefinitions(); }
    @Override public Object getDefaultValue() { return getResolved().getDefaultValue(); }
    @Override public String getContentMediaType() { return getResolved().getContentMediaType(); }

    @Override
    public String getFormat() {
        SchemaNode target = getResolved();
        return (target != null) ? target.getFormat() : "";
    }

    @Override
    public String getFormatOption(String key) {
        SchemaNode target = getResolved();
        return (target != null) ? target.getFormatOption(key) : "";
    }

    @Override
    public Object getExtension(String key) {
        // 1. Check the reference site first (local hints)
        Object local = super.getExtension(key);
        if (local != null) return local;

        // 2. Fallback to the resolved target's hints
        SchemaNode resolved = getResolved();
        return (resolved != null) ? resolved.getExtension(key) : null;
    }

    //
    //
    //

    @Override
    public Map<String, Object> getExtensions() {
        // Merge local hints with target hints for a complete picture
        Map<String, Object> combined = new HashMap<>(super.getExtensions());
        try {
            SchemaNode target = getResolved();
            if (target != null) {
                // Local hints override target hints in case of conflict
                target.getExtensions().forEach(combined::putIfAbsent);
            }
        } catch (Exception ignored) {}
        return combined;
    }
}
