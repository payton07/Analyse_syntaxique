package hai913i.tp1.visitors;

import hai913i.tp1.model.infos.CallSiteInfo;
import hai913i.tp1.utils.MethodSignatureUtil;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class CallExtractorVisitor extends ASTVisitor {

    private final Map<String, List<CallSiteInfo>> callsByMethod = new LinkedHashMap<>();
    private final Deque<String> currentMethodStack = new ArrayDeque<>();
    private final Deque<ITypeBinding> currentTypeStack = new ArrayDeque<>();

    // ------------------------------------------------------------------------
    // Types englobants
    // ------------------------------------------------------------------------
    @Override
    public boolean visit(TypeDeclaration node) {
        currentTypeStack.push(node.resolveBinding());
        return super.visit(node);
    }

    @Override
    public void endVisit(TypeDeclaration node) {
        currentTypeStack.pop();
        super.endVisit(node);
    }

    // ------------------------------------------------------------------------
    // Déclarations de méthodes
    // ------------------------------------------------------------------------
    @Override
    public boolean visit(MethodDeclaration node) {
        if (isInsideAnonymousClass(node)) {
            return super.visit(node);
        }

        String methodKey = MethodSignatureUtil.getMethodKey(node.resolveBinding());
        currentMethodStack.push(methodKey);
        callsByMethod.putIfAbsent(methodKey, new ArrayList<>());

        return super.visit(node);
    }

    @Override
    public void endVisit(MethodDeclaration node) {
        if (!isInsideAnonymousClass(node) && !currentMethodStack.isEmpty()) {
            currentMethodStack.pop();
        }
        super.endVisit(node);
    }

    // ------------------------------------------------------------------------
    // Invocations
    // ------------------------------------------------------------------------
    @Override
    public boolean visit(MethodInvocation node) {
        if (currentMethodStack.isEmpty()) {
            return super.visit(node);
        }
        registerCall(node, node.getName(), node.resolveMethodBinding(), resolveReceiver(node));
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        if (currentMethodStack.isEmpty()) {
            return super.visit(node);
        }
        ITypeBinding enclosing = currentTypeStack.peek();
        String receiverType = (enclosing != null && enclosing.getSuperclass() != null)
                ? getTypeName(enclosing.getSuperclass(), "super")
                : "super";

        registerCall(node, node.getName(), node.resolveMethodBinding(), receiverType);
        return super.visit(node);
    }

    public Map<String, List<CallSiteInfo>> getCallsByMethod() {
        return callsByMethod;
    }

    // ------------------------------------------------------------------------
    // Méthodes d'aide
    // ------------------------------------------------------------------------
    private String resolveReceiver(MethodInvocation node) {
        Expression expr = node.getExpression();
        if (expr != null) {
            return getTypeName(expr.resolveTypeBinding(), "<type non résolu>");
        }

        IMethodBinding mb = node.resolveMethodBinding();
        if (mb != null && Modifier.isStatic(mb.getModifiers())) {
            return getTypeName(mb.getDeclaringClass(), "<inconnu>");
        }

        return getTypeName(currentTypeStack.peek(), "this");
    }

    private void registerCall(ASTNode node, SimpleName name, IMethodBinding binding, String receiverType) {
        CompilationUnit cu = (CompilationUnit) node.getRoot();

        CallSiteInfo info = new CallSiteInfo();
        info.methodName = name.getIdentifier();
        info.lineNumber = cu.getLineNumber(node.getStartPosition());
        info.receiverStaticType = receiverType;
        info.resolvedTarget = MethodSignatureUtil.getMethodKey(binding);

        callsByMethod.get(currentMethodStack.peek()).add(info);
    }

    private String getTypeName(ITypeBinding type, String fallback) {
        return (type != null) ? type.getErasure().getQualifiedName() : fallback;
    }

    private boolean isInsideAnonymousClass(ASTNode node) {
        ASTNode parent = node.getParent();
        while (parent != null) {
            if (parent instanceof AnonymousClassDeclaration) return true;
            if (parent instanceof TypeDeclaration) return false;
            parent = parent.getParent();
        }
        return false;
    }
}