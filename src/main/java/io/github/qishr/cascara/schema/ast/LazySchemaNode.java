package io.github.qishr.cascara.schema.ast;

import io.github.qishr.cascara.common.lang.ast.AstNode;
import io.github.qishr.cascara.schema.api.SchemaResolver;
import io.github.qishr.cascara.schema.rule.ValidationRule;
import io.github.qishr.cascara.schema.util.SchemaException;
import io.github.qishr.cascara.schema.util.ValidationResult;

import java.net.URI;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;


public class LazySchemaNode extends BaseSchemaNode {
    private final SchemaResolver resolver;
    private SchemaNode resolvedNode;
    private SchemaNode root;

    public LazySchemaNode(String ref, SchemaResolver resolver, SchemaNode root, URI originUri) {
        super(ref, null);
        // if (root == null) {
        //     throw new SchemaException("BUG: NULL ROOT", ref);
        // }
        this.ref = ref;
        this.resolver = resolver;
        this.root = root;
        this.originUri = originUri;
    }

    public void setRoot(SchemaNode root) {
        this.root = root;
    }

    public SchemaNode getRoot() {
        return root;
    }

    public SchemaNode getResolved() throws SchemaException {
        if (resolvedNode == null) {
            AstNode result;
            result = resolver.resolve(ref, this);
            if (result instanceof SchemaNode sn) {
                this.resolvedNode = sn;
            } else {
                // Fallback or error if the resolver returned raw AST
                // instead of a compiled schema
                throw new SchemaException("BUG: RESOLUTION FAILED", ref);
            }
        }
        return resolvedNode;
    }

    @Override
    public URI getUri() {
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

    // @Override
    // public AstNode getOriginAst() {
    //     // If we haven't resolved yet, we fall back to the root's origin
    //     // to give the resolver context.
    //     return (resolvedNode != null) ? resolvedNode.getOriginAst() : root.getOriginAst();
    // }

    @Override
    public AstNode getOriginAst() {
        if (resolvedNode != null) {
            return resolvedNode.getOriginAst();
        }

        // Break the infinite recursion:
        // Only ask the root if the root isn't US.
        if (root != null && root != this) {



            // The resolver gets "relativeTo.getOriginAst();" where relativeTo is the lazy node.
            // The lazy node in question has a root, but the root has no originAst.
            // Surely in this case the root is the origin.
            // if (root.getOriginAst() == null) {
            //     return root;
            // }
            // That is not right - we need the war AST, not the compiled schema (which root is)



            return root.getOriginAst();
        }

        // Fallback: If we are the root or have no root, we have no
        // parent AST to provide context yet.
        return null;
    }

    public SchemaType getType() {
        SchemaNode resolved = getResolved();
        if (resolved == null) {
            throw new IllegalStateException("Attempted to access type on LazySchemaNode before it was resolved.");
        }
        return resolved.getType();
    }

    @Override public String getName() { return getResolved().getName(); }
    @Override public Optional<SchemaNode> getProperty(String key) { return getResolved().getProperty(key); }
    @Override public Map<String, SchemaNode> getProperties() { return getResolved().getProperties(); }
    @Override public SchemaNode getItemTemplate() { return getResolved().getItemTemplate(); }
    @Override public boolean isRef() { return true; }
    @Override public String getRef() { return this.ref; }
    @Override public List<ValidationRule> getRules() { return getResolved().getRules(); }
    @Override public String getTitle() { return getResolved().getTitle(); }
    @Override public String getDescription() { return getResolved().getDescription(); }
    @Override public Map<String, SchemaNode> getDefinitions() { return getResolved().getDefinitions(); }
    @Override public Object getDefaultValue() { return getResolved().getDefaultValue(); }

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
    public Object getCustomHint(String key) {
        // 1. Check the reference site first (local hints)
        Object local = super.getCustomHint(key);
        if (local != null) return local;

        // 2. Fallback to the resolved target's hints
        SchemaNode resolved = getResolved();
        return (resolved != null) ? resolved.getCustomHint(key) : null;
    }

    //
    //
    //

    @Override
    public Map<String, Object> getCustomHints() {
        // Merge local hints with target hints for a complete picture
        Map<String, Object> combined = new HashMap<>(super.getCustomHints());
        try {
            SchemaNode target = getResolved();
            if (target != null) {
                // Local hints override target hints in case of conflict
                target.getCustomHints().forEach(combined::putIfAbsent);
            }
        } catch (Exception ignored) {}
        return combined;
    }
}
