package hai913i.tp1.visitors;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.AbstractTypeDeclaration;
import org.eclipse.jdt.core.dom.AnnotationTypeDeclaration;
import org.eclipse.jdt.core.dom.CompilationUnit;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;

/**
 * Règles de comptage du § 4.1, partagées par tous les visiteurs.
 */
public final class CountingRules {

    public static final String DEFAULT_PACKAGE = "(défaut)";

    private CountingRules() {}

    // Type compté : classe, interface, énumération ou record nommé, de premier niveau ou membre.
    // Exclus : types d'annotation, classes locales, classes anonymes (et tout ce qu'elles contiennent).
    public static boolean isCountedType(AbstractTypeDeclaration node) {
        return !(node instanceof AnnotationTypeDeclaration) && isTopLevelOrMember(node);
    }

    // Méthode comptée : écrite directement dans un type compté.
    public static boolean isCountedMethod(MethodDeclaration node) {
        return node.getParent() instanceof AbstractTypeDeclaration type && isCountedType(type);
    }

    // Attribut compté : déclaré directement dans un type compté.
    public static boolean isCountedField(FieldDeclaration node) {
        return node.getParent() instanceof AbstractTypeDeclaration type && isCountedType(type);
    }

    private static boolean isTopLevelOrMember(AbstractTypeDeclaration node) {
        ASTNode parent = node.getParent();
        if (parent instanceof CompilationUnit) return true;
        // Un parent TypeDeclarationStatement (classe locale) ou AnonymousClassDeclaration arrête la remontée.
        return parent instanceof AbstractTypeDeclaration outer && isTopLevelOrMember(outer);
    }
}
