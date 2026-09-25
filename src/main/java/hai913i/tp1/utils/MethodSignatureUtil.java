package hai913i.tp1.utils;

import org.eclipse.jdt.core.dom.IMethodBinding;
import org.eclipse.jdt.core.dom.ITypeBinding;
import java.util.StringJoiner;

public final class MethodSignatureUtil {

    private MethodSignatureUtil() {}

    public static String getMethodKey(IMethodBinding binding) {
        if (binding == null) {
            return "non résolue";
        }

        // Remonter à la déclaration originale (élimine les types génériques instanciés)
        binding = binding.getMethodDeclaration();

        // 1. Classe ou interface qui déclare la méthode
        ITypeBinding declaringClass = binding.getDeclaringClass();
        String className = (declaringClass != null)
                ? declaringClass.getErasure().getQualifiedName()
                : "<inconnu>";

        // 2. Nom de la méthode (<init> si constructeur)
        String methodName = binding.isConstructor() ? "<init>" : binding.getName();

        // 3. Types des paramètres avec effacement de type (erasure)
        StringJoiner params = new StringJoiner(",");
        for (ITypeBinding paramType : binding.getParameterTypes()) {
            params.add(paramType.getErasure().getQualifiedName());
        }

        return className + "#" + methodName + "(" + params + ")";
    }
}