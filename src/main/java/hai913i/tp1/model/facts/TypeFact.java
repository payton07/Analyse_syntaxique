package hai913i.tp1.model.facts;

import java.util.Collections;
import java.util.List;

public record TypeFact(
        String qualifiedName,
        String kind,
        String packageName,
        List<String> superClasses,
        List<String> interfaces,
        List<FieldFact> fields,
        List<String> methodKeys
) implements Comparable<TypeFact> {

    public TypeFact {
        superClasses = Collections.unmodifiableList(superClasses);
        interfaces = Collections.unmodifiableList(interfaces);
        fields = Collections.unmodifiableList(fields);
        methodKeys = Collections.unmodifiableList(methodKeys);
    }

    public boolean isInterface() {
        return "Interface".equalsIgnoreCase(kind);
    }

    public boolean isEnum() {
        return "Énumération".equalsIgnoreCase(kind);
    }

    public int getFieldCount() {
        return fields.size();
    }

    public int getMethodCount() {
        return methodKeys.size();
    }

    @Override
    public int compareTo(TypeFact o) {
        return this.qualifiedName.compareTo(o.qualifiedName);
    }
}