package hai913i.tp1.analysis;

import hai913i.tp1.model.facts.CallFact;
import hai913i.tp1.model.facts.MethodFact;
import hai913i.tp1.model.facts.ProjectFacts;

import java.util.*;

/**
 * Graphe d'appel du projet (B3), construit à partir du seul modèle de faits (§ 4.4).
 *
 * Nœuds : les méthodes comptées du projet, désignées par leur clé.
 * Arc f -> g : le corps de f contient au moins un appel dont la cible résolue est g ;
 * son poids est le nombre de sites d'appel.
 */
public class CallGraph {

    public record Edge(String source, String target, int weight) {
    }

    // Les deux sens sont stockés : appelant -> (appelée -> poids) et appelée -> (appelant -> poids).
    // TreeMap partout : parcours dans l'ordre des clés, donc sortie identique d'une exécution à l'autre.
    private final Map<String, Map<String, Integer>> callees = new TreeMap<>();
    private final Map<String, Map<String, Integer>> callers = new TreeMap<>();

    private int internalCallSites = 0;
    private int externalCalls = 0;
    private int unresolvedCalls = 0;

    public CallGraph(ProjectFacts facts, boolean withConstructorArcs) {
        // 1. Un nœud par méthode comptée, même si elle n'appelle rien et n'est appelée par personne
        for (String methodKey : facts.getMethods().keySet()) {
            callees.put(methodKey, new TreeMap<>());
            callers.put(methodKey, new TreeMap<>());
        }

        // 2. Chaque site d'appel est classé : non résolu, externe, ou interne (arc)
        for (MethodFact caller : facts.getMethods().values()) {
            for (CallFact call : caller.calls()) {
                addCall(facts, caller.methodKey(), call);
            }
            if (withConstructorArcs) {
                for (CallFact call : caller.constructorCalls()) {
                    addCall(facts, caller.methodKey(), call);
                }
            }
        }
    }

    private void addCall(ProjectFacts facts, String source, CallFact call) {
        if (!call.isResolved()) {
            unresolvedCalls++;
        } else if (!facts.isInternalMethod(call.resolvedTarget())) {
            // Cible hors du projet (JDK, bibliothèque) : comptée, mais ni nœud ni arc
            externalCalls++;
        } else {
            internalCallSites++;
            String target = call.resolvedTarget();
            // merge : crée l'arc avec le poids 1, ou ajoute 1 au poids s'il existe déjà
            callees.get(source).merge(target, 1, Integer::sum);
            callers.get(target).merge(source, 1, Integer::sum);
        }
    }

    public Set<String> getNodes() {
        return Collections.unmodifiableSet(callees.keySet());
    }

    public List<Edge> getEdges() {
        List<Edge> edges = new ArrayList<>();
        for (Map.Entry<String, Map<String, Integer>> source : callees.entrySet()) {
            for (Map.Entry<String, Integer> target : source.getValue().entrySet()) {
                edges.add(new Edge(source.getKey(), target.getKey(), target.getValue()));
            }
        }
        return edges;
    }

    // Poids de l'arc source -> target, 0 s'il n'existe pas
    public int getWeight(String source, String target) {
        return callees.getOrDefault(source, Map.of()).getOrDefault(target, 0);
    }

    // Appelées de la méthode : ce qu'elle nomme dans son corps (graphe direct)
    public Map<String, Integer> getCallees(String methodKey) {
        return Collections.unmodifiableMap(callees.getOrDefault(methodKey, Map.of()));
    }

    // Appelantes de la méthode : qui la nomme (graphe inversé, celui de l'analyse d'impact)
    public Map<String, Integer> getCallers(String methodKey) {
        return Collections.unmodifiableMap(callers.getOrDefault(methodKey, Map.of()));
    }

    // Nœuds dont la clé contient le texte donné : évite de saisir une clé complète
    public List<String> findNodes(String fragment) {
        return callees.keySet().stream()
                .filter(key -> key.contains(fragment))
                .toList();
    }

    public int getInternalCallSites() {
        return internalCallSites;
    }

    public int getExternalCalls() {
        return externalCalls;
    }

    public int getUnresolvedCalls() {
        return unresolvedCalls;
    }
}
