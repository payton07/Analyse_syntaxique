package hai913i.tp1.model.facts;

import java.util.Collections;
import java.util.List;

public record MethodFact(
        String methodKey,
        String simpleName,
        String declaringType,
        boolean isConstructor,
        int parameterCount,
        List<CallFact> calls
) implements Comparable<MethodFact> {

    public MethodFact {
        calls = Collections.unmodifiableList(calls);
    }

    @Override
    public int compareTo(MethodFact o) {
        return this.methodKey.compareTo(o.methodKey);
    }
}