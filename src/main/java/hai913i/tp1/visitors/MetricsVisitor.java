package hai913i.tp1.visitors;

import hai913i.tp1.analysis.ProjectStats;
import org.eclipse.jdt.core.dom.*;

public class MetricsVisitor extends ASTVisitor {
    private final ProjectStats stats = new ProjectStats();

    public ProjectStats getStats() {
        return stats;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        if (CountingRules.isCountedType(node)) {
            if (node.isInterface()) {
                stats.incrementInterfaces();
            } else {
                stats.incrementClasses();
            }
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        if (CountingRules.isCountedType(node)) {
            stats.incrementEnums();
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(RecordDeclaration node) {
        if (CountingRules.isCountedType(node)) {
            stats.incrementRecords();
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        if (CountingRules.isCountedMethod(node)) {
            if (node.isConstructor()) {
                stats.incrementConstructors();
            } else {
                stats.incrementMethods();
            }
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        if (CountingRules.isCountedField(node)) {
            stats.addAttributes(node.fragments().size());
        }
        return super.visit(node);
    }
}
