package hai913i.tp1.model;

import java.util.ArrayList;
import java.util.List;

public class ClassInfo {
    public String qualifiedName;
    public String kind; // Class, Interface, Enum, Record
    public String packageName;
    public List<String> superClasses = new ArrayList<>();
    public List<String> interfaces = new ArrayList<>();
    public List<FieldInfo> fields = new ArrayList<>();
    public List<MethodInfo> methods = new ArrayList<>();

    public static class FieldInfo {
        public String name;
        public String declaredType;
        public String visibility;

        @Override
        public String toString() {
            return String.format("  - Attribut: %s %s [visibilité: %s]", declaredType, name, visibility);
        }
    }

    public static class MethodInfo {
        public String name;
        public int parameterCount;
        public boolean isConstructor;

        @Override
        public String toString() {
            return String.format("  - %s: %s (paramètres: %d)",
                    isConstructor ? "Constructeur" : "Méthode", name, parameterCount);
        }
    }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder();
        sb.append("==================================================\n");
        sb.append(String.format("%s : %s (package: %s)\n", kind, qualifiedName, packageName));

        sb.append("Superclasses : ")
                .append(superClasses.isEmpty() ? "aucune" : String.join(", ", superClasses))
                .append("\n");

        sb.append("Interfaces directes : ")
                .append(interfaces.isEmpty() ? "aucune" : String.join(", ", interfaces))
                .append("\n");

        sb.append("Attributs (").append(fields.size()).append(") :\n");
        for (FieldInfo f : fields) sb.append(f).append("\n");

        sb.append("Méthodes (").append(methods.size()).append(") :\n");
        for (MethodInfo m : methods) sb.append(m).append("\n");

        return sb.toString();
    }
}
