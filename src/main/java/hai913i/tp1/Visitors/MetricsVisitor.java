package hai913i.tp1.Visitors;

import hai913i.tp1.model.ProjectStats;
import org.eclipse.jdt.core.dom.*;

public class MetricsVisitor extends ASTVisitor {
    private final ProjectStats stats = new ProjectStats();

    public ProjectStats getStats() {
        return stats;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        if (node.isInterface()) {
            stats.incrementInterfaces();
        } else {
            stats.incrementClasses();
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        stats.incrementEnums();
        return super.visit(node);
    }

    @Override
    public boolean visit(RecordDeclaration node) {
        stats.incrementRecords();
        return super.visit(node);
    }

    @Override
    public boolean visit(MethodDeclaration node) {
        if (node.isConstructor()) {
            stats.incrementConstructors();
        } else {
            stats.incrementMethods();
        }
        return super.visit(node);
    }

    @Override
    public boolean visit(FieldDeclaration node) {
        stats.addAttributes(node.fragments().size());
        return super.visit(node);
    }
}