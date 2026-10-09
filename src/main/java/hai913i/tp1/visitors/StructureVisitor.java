package hai913i.tp1.visitors;

import hai913i.tp1.model.infos.ClassInfo;
import hai913i.tp1.model.infos.ClassInfo.FieldInfo;
import hai913i.tp1.model.infos.ClassInfo.MethodInfo;
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
        addType(node, node.isInterface() ? "Interface" : "Classe",
                node.getSuperclassType(), node.superInterfaceTypes());
        return super.visit(node);
    }

    @Override
    public boolean visit(EnumDeclaration node) {
        addType(node, "Énumération", null, node.superInterfaceTypes());
        return super.visit(node);
    }

    @Override
    public boolean visit(RecordDeclaration node) {
        addType(node, "Record", null, node.superInterfaceTypes());
        return super.visit(node);
    }

    private void addType(AbstractTypeDeclaration node, String kind,
                         Type superclassType, List<?> superInterfaces) {
        // Classes locales et membres de classes anonymes : non comptés (§ 4.1)
        if (!CountingRules.isCountedType(node)) return;

        ClassInfo info = new ClassInfo();
        info.kind = kind;
        populateDetails(node, superclassType, superInterfaces, info);
        classes.add(info);
    }

    private void populateDetails(AbstractTypeDeclaration node, Type superclassType,
                                 List<?> superInterfaces, ClassInfo info) {
        ITypeBinding binding = node.resolveBinding();
        if (binding != null) {
            info.qualifiedName = binding.getQualifiedName();
            IPackageBinding pkg = binding.getPackage();
            info.packageName = (pkg != null && !pkg.isUnnamed()) ? pkg.getName() : CountingRules.DEFAULT_PACKAGE;

            ITypeBinding current = binding.getSuperclass();
            while (current != null && !"java.lang.Object".equals(current.getQualifiedName())) {
                info.superClasses.add(current.getQualifiedName());
                current = current.getSuperclass();
            }

            for (ITypeBinding itf : binding.getInterfaces()) {
                info.interfaces.add(itf.getQualifiedName());
            }
        } else {
            CompilationUnit cu = (CompilationUnit) node.getRoot();
            info.qualifiedName = node.getName().getFullyQualifiedName();
            info.packageName = (cu.getPackage() != null)
                    ? cu.getPackage().getName().getFullyQualifiedName()
                    : CountingRules.DEFAULT_PACKAGE;

            if (superclassType != null) {
                info.superClasses.add(superclassType.toString());
            }
            for (Object obj : superInterfaces) {
                info.interfaces.add(obj.toString());
            }
        }

        // Membres lus dans bodyDeclarations() : ceux des types imbriqués ne sont pas attribués à ce type.
        // Les constantes d'énumération et les composants d'un record n'y figurent pas.
        boolean inInterface = "Interface".equals(info.kind);
        for (Object decl : node.bodyDeclarations()) {
            if (decl instanceof FieldDeclaration fd) {
                addFields(fd, inInterface, info);
            } else if (decl instanceof MethodDeclaration md) {
                MethodInfo mi = new MethodInfo();
                mi.name = md.getName().getIdentifier();
                mi.parameterCount = md.parameters().size();
                mi.isConstructor = md.isConstructor();
                info.methods.add(mi);
            }
        }
    }

    private void addFields(FieldDeclaration fd, boolean inInterface, ClassInfo info) {
        String visibility = getVisibility(fd.modifiers(), inInterface);
        String declaredType = fd.getType().toString();
        // int a, b; compte pour deux attributs
        for (Object f : fd.fragments()) {
            VariableDeclarationFragment fragment = (VariableDeclarationFragment) f;
            FieldInfo fi = new FieldInfo();
            fi.name = fragment.getName().getIdentifier();
            fi.declaredType = declaredType;
            fi.visibility = visibility;
            info.fields.add(fi);
        }
    }

    private String getVisibility(List<?> modifiers, boolean inInterface) {
        for (Object mod : modifiers) {
            if (mod instanceof Modifier m) {
                if (m.isPublic()) return "public";
                if (m.isProtected()) return "protected";
                if (m.isPrivate()) return "private";
            }
        }
        return inInterface ? "public implicite" : "package";
    }
}
