package hai913i.tp1.model.facts;

import java.util.Collections;
import java.util.List;

public record MethodFact(
        String methodKey,
        String simpleName,
        String declaringType,
        boolean isConstructor,
        int parameterCount,
        int linesOfCode,       // Nombre de lignes du corps (0 si abstrait/interface)
        boolean hasBody,       // true si méthode concrète
        List<CallFact> calls,            // Appels du § 4.4 (m(), a.m(), super.m())
        List<CallFact> constructorCalls  // new, this(...), super(...) : option du graphe d'appel
) implements Comparable<MethodFact> {

    public MethodFact {
        calls = Collections.unmodifiableList(calls);
        constructorCalls = Collections.unmodifiableList(constructorCalls);
    }

    @Override
    public int compareTo(MethodFact o) {
        return this.methodKey.compareTo(o.methodKey);
    }
}