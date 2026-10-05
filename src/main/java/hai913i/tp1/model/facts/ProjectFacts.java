package hai913i.tp1.model.facts;

import java.util.*;

public class ProjectFacts {

    private final Map<String, TypeFact> types;
    private final Map<String, MethodFact> methods;

    public ProjectFacts(Map<String, TypeFact> types, Map<String, MethodFact> methods) {
        this.types = Collections.unmodifiableMap(new TreeMap<>(types));
        this.methods = Collections.unmodifiableMap(new TreeMap<>(methods));
    }

    public Map<String, TypeFact> getTypes() {
        return types;
    }

    public Map<String, MethodFact> getMethods() {
        return methods;
    }

    public boolean isInternalMethod(String methodKey) {
        return methods.containsKey(methodKey);
    }

    public List<CallFact> getAllCalls() {
        List<CallFact> all = new ArrayList<>();
        for (MethodFact mf : methods.values()) {
            all.addAll(mf.calls());
        }
        return Collections.unmodifiableList(all);
    }
}