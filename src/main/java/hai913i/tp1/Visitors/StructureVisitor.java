package hai913i.tp1.Visitors;

import hai913i.tp1.model.ClassInfo;
import hai913i.tp1.model.ClassInfo.FieldInfo;
import hai913i.tp1.model.ClassInfo.MethodInfo;
import org.eclipse.jdt.core.dom.*;

import java.util.ArrayList;
import java.util.List;

public class StructureVisitor extends ASTVisitor {
    private final List<ClassInfo> classes = new ArrayList<>();

    public List<ClassInfo> getClasses() {
        return classes;
    }

    @Override
    public boolean visit(TypeDeclaration node) {
        ClassInfo info = new ClassInfo();
        info.kind = node.isInterface() ? "Interface" : "Classe";
        populateDetails(node.resolveBinding(), node.getName().getFullyQualifiedName(),
                (CompilationUnit) node.getRoot(), node.getSuperclassType(),
                node.superInterfaceTypes(), node.getFields(), node.getMethods(), info);
        classes.add(info);
        return super.visit(node);
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        ClassInfo info = new ClassInfo();
        info.kind = "Énumération";

        List<FieldDeclaration> fields = new ArrayList<>();
        List<MethodDeclaration> methods = new ArrayList<>();
        for (Object decl : node.bodyDeclarations()) {
            if (decl instanceof FieldDeclaration fd) fields.add(fd);
            else if (decl instanceof MethodDeclaration md) methods.add(md);
        }

        populateDetails(node.resolveBinding(), node.getName().getFullyQualifiedName(),
                (CompilationUnit) node.getRoot(), null,
                node.superInterfaceTypes(),
                fields.toArray(new FieldDeclaration[0]),
                methods.toArray(new MethodDeclaration[0]), info);
        classes.add(info);
        return super.visit(node);
    }

    private void populateDetails(ITypeBinding binding, String fallbackName, CompilationUnit cu,
                                 Type superclassType, List<?> superInterfaces,
                                 FieldDeclaration[] fields, MethodDeclaration[] methods,
                                 ClassInfo info) {
        if (binding != null) {
            info.qualifiedName = binding.getQualifiedName();
            IPackageBinding pkg = binding.getPackage();
            info.packageName = (pkg != null && !pkg.isUnnamed()) ? pkg.getName() : "<default>";

            ITypeBinding current = binding.getSuperclass();
            while (current != null && !"java.lang.Object".equals(current.getQualifiedName())) {
                info.superClasses.add(current.getQualifiedName());
                current = current.getSuperclass();
            }

            for (ITypeBinding itf : binding.getInterfaces()) {
                info.interfaces.add(itf.getQualifiedName());
            }
        } else {
            info.qualifiedName = fallbackName;
            info.packageName = (cu.getPackage() != null) ? cu.getPackage().getName().getFullyQualifiedName() : "<default>";

            if (superclassType != null) {
                info.superClasses.add(superclassType.toString());
            }
            for (Object obj : superInterfaces) {
                info.interfaces.add(obj.toString());
            }
        }

        // Attributs
        for (FieldDeclaration fd : fields) {
            String visibility = getVisibility(fd.modifiers());
            String declaredType = fd.getType().toString();
            for (Object f : fd.fragments()) {
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) f;
                FieldInfo fi = new FieldInfo();
                fi.name = fragment.getName().getIdentifier();
                fi.declaredType = declaredType;
                fi.visibility = visibility;
                info.fields.add(fi);
            }
        }

        // Méthodes
        for (MethodDeclaration md : methods) {
            MethodInfo mi = new MethodInfo();
            mi.name = md.getName().getIdentifier();
            mi.parameterCount = md.parameters().size();
            mi.isConstructor = md.isConstructor();
            info.methods.add(mi);
        }
    }

    private String getVisibility(List<?> modifiers) {
        for (Object mod : modifiers) {
            if (mod instanceof Modifier m) {
                if (m.isPublic()) return "public";
                if (m.isProtected()) return "protected";
                if (m.isPrivate()) return "private";
            }
        }
        return "package-private";
    }
}