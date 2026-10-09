package hai913i.tp1.visitors;

import java.io.PrintStream;

import org.eclipse.jdt.core.dom.ASTNode;
import org.eclipse.jdt.core.dom.ASTVisitor;

public class TreeVisitor extends ASTVisitor {
    private final PrintStream out;
    private int depth = 0;

    public TreeVisitor(PrintStream out) {
        this.out = out;
    }

    @Override
    public void preVisit(ASTNode node) {
        out.print("  ".repeat(depth));
        out.println(ASTNode.nodeClassForType(node.getNodeType()).getSimpleName());
        depth++;
    }

    @Override
    public void postVisit(ASTNode node) {
        depth--;
    }
}
