package hai913i.tp1.visitors;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;

public class TreeVisitor extends ASTVisitor {
    private int depth = 0;

    @Override
    public void preVisit(ASTNode node) {
        System.out.print("  ".repeat(depth));
        System.out.println(ASTNode.nodeClassForType(node.getNodeType()).getSimpleName());
        depth++;
    }

    @Override
    public void postVisit(ASTNode node) {
        depth--;
    }
}