package hai913i.tp1.parse;

import hai913i.tp1.model.ClassInfo;
import org.eclipse.jdt.core.dom.*;
import hai913i.tp1.model.ClassInfo.FieldInfo;
import hai913i.tp1.model.ClassInfo.MethodInfo;

import java.util.ArrayList;
import java.util.List;

public class TreeVisitor extends ASTVisitor {
    private int depth = 0;
    private final List<ClassInfo> classes = new ArrayList<>();
    public List<ClassInfo> getClasses() {
        return classes;
    }

    @Override
    public boolean preVisit2(ASTNode node) {
        System.out.print("  ".repeat(depth));

        String nodeType = ASTNode.nodeClassForType(node.getNodeType()).getSimpleName();
        String details = getNodeDetails(node);

        if (details.isEmpty()) {
            System.out.println(nodeType);
        } else {
            System.out.println(nodeType + " [" + details + "]");
        }

        depth++;
        return true;
    }

    @Override
    public void postVisit(ASTNode node) {
        depth--;
    }

    private String getNodeDetails(ASTNode node) {
        if (node instanceof SimpleName name) {
            return name.getIdentifier();
        } else if (node instanceof StringLiteral literal) {
            return literal.getEscapedValue();
        } else if (node instanceof PrimitiveType type) {
            return type.getPrimitiveTypeCode().toString();
        }
        return "";
    }

    // ------------------------------------------------------------------------
    // Classes & Interfaces classiques
    // ------------------------------------------------------------------------
    @Override
    public boolean visit(TypeDeclaration node) {
        ClassInfo info = new ClassInfo();
        ITypeBinding binding = node.resolveBinding();

        // 1. Genre, Nom qualifié et Package
        info.kind = node.isInterface() ? "Interface" : "Classe";
        if (binding != null) {
            info.qualifiedName = binding.getQualifiedName();
            IPackageBinding pkg = binding.getPackage();
            info.packageName = (pkg != null && !pkg.isUnnamed()) ? pkg.getName() : "<default>";

            // 2. Superclasse et Interfaces directes (via liaisons)
            ITypeBinding superType = binding.getSuperclass();
            if (superType != null) {
                info.superClass = superType.getQualifiedName();
            }
            for (ITypeBinding intf : binding.getInterfaces()) {
                info.interfaces.add(intf.getQualifiedName());
            }
        } else {
            // Repli sans résolution de liaisons
            info.qualifiedName = node.getName().getFullyQualifiedName();
            CompilationUnit cu = (CompilationUnit) node.getRoot();
            info.packageName = cu.getPackage() != null ? cu.getPackage().getName().getFullyQualifiedName() : "<default>";

            if (node.getSuperclassType() != null) {
                info.superClass = node.getSuperclassType().toString();
            }
            for (Object obj : node.superInterfaceTypes()) {
                info.interfaces.add(obj.toString());
            }
        }

        // 3. Attributs
        extractFields(node.getFields(), info);

        // 4. Méthodes & Constructeurs
        extractMethods(node.getMethods(), info);

        classes.add(info);
        return super.visit(node);
    }

    // ------------------------------------------------------------------------
    // Énumérations
    // ------------------------------------------------------------------------
    @Override
    public boolean visit(EnumDeclaration node) {
        ClassInfo info = new ClassInfo();
        ITypeBinding binding = node.resolveBinding();

        info.kind = "Énumération";
        if (binding != null) {
            info.qualifiedName = binding.getQualifiedName();
            IPackageBinding pkg = binding.getPackage();
            info.packageName = (pkg != null && !pkg.isUnnamed()) ? pkg.getName() : "<default>";
            info.superClass = "java.lang.Enum";
            for (ITypeBinding intf : binding.getInterfaces()) {
                info.interfaces.add(intf.getQualifiedName());
            }
        } else {
            info.qualifiedName = node.getName().getFullyQualifiedName();
            CompilationUnit cu = (CompilationUnit) node.getRoot();
            info.packageName = cu.getPackage() != null ? cu.getPackage().getName().getFullyQualifiedName() : "<default>";
            info.superClass = "Enum";
            for (Object obj : node.superInterfaceTypes()) {
                info.interfaces.add(obj.toString());
            }
        }

        // Extraire les attributs déclarés dans l'enum
        List<FieldDeclaration> enumFields = new ArrayList<>();
        List<MethodDeclaration> enumMethods = new ArrayList<>();
        for (Object bodyDecl : node.bodyDeclarations()) {
            if (bodyDecl instanceof FieldDeclaration fd) enumFields.add(fd);
            else if (bodyDecl instanceof MethodDeclaration md) enumMethods.add(md);
        }
        extractFields(enumFields.toArray(new FieldDeclaration[0]), info);
        extractMethods(enumMethods.toArray(new MethodDeclaration[0]), info);

        classes.add(info);
        return super.visit(node);
    }

    // ------------------------------------------------------------------------
    // Records (Java 14+)
    // ------------------------------------------------------------------------
    @Override
    public boolean visit(RecordDeclaration node) {
        ClassInfo info = new ClassInfo();
        ITypeBinding binding = node.resolveBinding();

        info.kind = "Record";
        if (binding != null) {
            info.qualifiedName = binding.getQualifiedName();
            IPackageBinding pkg = binding.getPackage();
            info.packageName = (pkg != null && !pkg.isUnnamed()) ? pkg.getName() : "<default>";
            info.superClass = "java.lang.Record";
            for (ITypeBinding intf : binding.getInterfaces()) {
                info.interfaces.add(intf.getQualifiedName());
            }
        } else {
            info.qualifiedName = node.getName().getFullyQualifiedName();
            CompilationUnit cu = (CompilationUnit) node.getRoot();
            info.packageName = cu.getPackage() != null ? cu.getPackage().getName().getFullyQualifiedName() : "<default>";
            info.superClass = "Record";
            for (Object obj : node.superInterfaceTypes()) {
                info.interfaces.add(obj.toString());
            }
        }

        List<FieldDeclaration> recordFields = new ArrayList<>();
        List<MethodDeclaration> recordMethods = new ArrayList<>();
        for (Object bodyDecl : node.bodyDeclarations()) {
            if (bodyDecl instanceof FieldDeclaration fd) recordFields.add(fd);
            else if (bodyDecl instanceof MethodDeclaration md) recordMethods.add(md);
        }
        extractFields(recordFields.toArray(new FieldDeclaration[0]), info);
        extractMethods(recordMethods.toArray(new MethodDeclaration[0]), info);

        classes.add(info);
        return super.visit(node);
    }

    // ------------------------------------------------------------------------
    // Méthodes utilitaires d'extraction
    // ------------------------------------------------------------------------
    private void extractFields(FieldDeclaration[] fieldDeclarations, ClassInfo target) {
        for (FieldDeclaration fd : fieldDeclarations) {
            String visibility = getVisibility(fd.modifiers());
            String declaredType = fd.getType().toString();

            // Une déclaration peut déclarer plusieurs variables (ex: int a, b = 2;)
            for (Object fragmentObj : fd.fragments()) {
                VariableDeclarationFragment fragment = (VariableDeclarationFragment) fragmentObj;
                FieldInfo fi = new FieldInfo();
                fi.name = fragment.getName().getIdentifier();
                fi.declaredType = declaredType;
                fi.visibility = visibility;
                target.fields.add(fi);
            }
        }
    }

    private void extractMethods(MethodDeclaration[] methodDeclarations, ClassInfo target) {
        for (MethodDeclaration md : methodDeclarations) {
            MethodInfo mi = new MethodInfo();
            mi.name = md.getName().getIdentifier();
            mi.parameterCount = md.parameters().size();
            mi.isConstructor = md.isConstructor();
            target.methods.add(mi);
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
