package hai913i.tp1.visitors;

import hai913i.tp1.model.infos.CallSiteInfo;
import hai913i.tp1.utils.MethodSignatureUtil;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

public class CallExtractorVisitor extends ASTVisitor {

    // Appels au sens du § 4.4 : MethodInvocation et SuperMethodInvocation
    private final Map<String, List<CallSiteInfo>> callsByMethod = new LinkedHashMap<>();
    // Appels de constructeurs (new, this(...), super(...)) : gardés à part, ils ne font pas partie
    // des appels du § 4.4 et ne servent qu'à l'option « arcs vers les constructeurs » du graphe (B3).
    private final Map<String, List<CallSiteInfo>> constructorCallsByMethod = new LinkedHashMap<>();

    private final Deque<String> currentMethodStack = new ArrayDeque<>();
    private final Deque<ITypeBinding> currentTypeStack = new ArrayDeque<>();

    // ------------------------------------------------------------------------
    // Types englobants
    // ------------------------------------------------------------------------
    // Seuls les types comptés (§ 4.1) sont empilés : le type englobant d'un appel écrit dans
    // une classe locale ou anonyme reste donc le type compté qui la contient.
    @Override
    public boolean visit(TypeDeclaration node) {
        enterType(node);
        return super.visit(node);
    }

    @Override
    public void endVisit(TypeDeclaration node) {
        leaveType(node);
        super.endVisit(node);
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        enterType(node);
        return super.visit(node);
    }

    @Override
    public void endVisit(EnumDeclaration node) {
        leaveType(node);
        super.endVisit(node);
    }

    @Override
    public boolean visit(RecordDeclaration node) {
        enterType(node);
        return super.visit(node);
    }

    @Override
    public void endVisit(RecordDeclaration node) {
        leaveType(node);
        super.endVisit(node);
    }

    private void enterType(AbstractTypeDeclaration node) {
        if (CountingRules.isCountedType(node)) {
            currentTypeStack.push(node.resolveBinding());
        }
    }

    private void leaveType(AbstractTypeDeclaration node) {
        if (CountingRules.isCountedType(node)) {
            currentTypeStack.pop();
        }
    }

    // ------------------------------------------------------------------------
    // Déclarations de méthodes
    // ------------------------------------------------------------------------
    @Override
    public boolean visit(MethodDeclaration node) {
        // Méthode d'une classe anonyme ou locale : ses appels sont attribués
        // à la méthode comptée qui la contient (§ 4.4).
        if (!CountingRules.isCountedMethod(node)) {
            return super.visit(node);
        }

        String methodKey = MethodSignatureUtil.getMethodKey(node.resolveBinding());
        currentMethodStack.push(methodKey);
        callsByMethod.putIfAbsent(methodKey, new ArrayList<>());
        constructorCallsByMethod.putIfAbsent(methodKey, new ArrayList<>());

        return super.visit(node);
    }

    @Override
    public void endVisit(MethodDeclaration node) {
        if (CountingRules.isCountedMethod(node) && !currentMethodStack.isEmpty()) {
            currentMethodStack.pop();
        }
        super.endVisit(node);
    }

    // ------------------------------------------------------------------------
    // Invocations de méthodes (§ 4.4)
    // ------------------------------------------------------------------------
    @Override
    public boolean visit(MethodInvocation node) {
        registerCall(callsByMethod, node, node.getName().getIdentifier(),
                node.resolveMethodBinding(), resolveReceiver(node));
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperMethodInvocation node) {
        registerCall(callsByMethod, node, node.getName().getIdentifier(),
                node.resolveMethodBinding(), superclassOfEnclosingType());
        return super.visit(node);
    }

    // ------------------------------------------------------------------------
    // Invocations de constructeurs (option du graphe d'appel)
    // ------------------------------------------------------------------------
    @Override
    public boolean visit(ClassInstanceCreation node) {
        // new T(...) : le « receveur » est le type instancié
        registerCall(constructorCallsByMethod, node, "<init>", node.resolveConstructorBinding(),
                getTypeName(node.getType().resolveBinding(), "<type non résolu>"));
        return super.visit(node);
    }

    @Override
    public boolean visit(ConstructorInvocation node) {
        // this(...) : autre constructeur du type englobant
        registerCall(constructorCallsByMethod, node, "<init>", node.resolveConstructorBinding(),
                getTypeName(currentTypeStack.peek(), "this"));
        return super.visit(node);
    }

    @Override
    public boolean visit(SuperConstructorInvocation node) {
        // super(...) : constructeur de la superclasse directe
        registerCall(constructorCallsByMethod, node, "<init>", node.resolveConstructorBinding(),
                superclassOfEnclosingType());
        return super.visit(node);
    }

    public Map<String, List<CallSiteInfo>> getCallsByMethod() {
        return callsByMethod;
    }

    public Map<String, List<CallSiteInfo>> getConstructorCallsByMethod() {
        return constructorCallsByMethod;
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

    private String superclassOfEnclosingType() {
        ITypeBinding enclosing = currentTypeStack.peek();
        return (enclosing != null) ? getTypeName(enclosing.getSuperclass(), "super") : "super";
    }

    private void registerCall(Map<String, List<CallSiteInfo>> target, ASTNode node, String name,
                              IMethodBinding binding, String receiverType) {
        // Hors de toute méthode comptée (initialiseur de champ) : appel ignoré (§ 4.4)
        if (currentMethodStack.isEmpty()) return;

        CompilationUnit cu = (CompilationUnit) node.getRoot();

        CallSiteInfo info = new CallSiteInfo();
        info.methodName = name;
        info.lineNumber = cu.getLineNumber(node.getStartPosition());
        info.receiverStaticType = receiverType;
        info.resolvedTarget = MethodSignatureUtil.getMethodKey(binding);

        target.get(currentMethodStack.peek()).add(info);
    }

    private String getTypeName(ITypeBinding type, String fallback) {
        return (type != null) ? type.getErasure().getQualifiedName() : fallback;
    }
}
