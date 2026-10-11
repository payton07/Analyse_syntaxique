package hai913i.tp1.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.TreeSet;

import hai913i.tp1.model.facts.MethodFact;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.model.facts.TypeFact;

/**
 * Vue d'un graphe d'appel : la partie du graphe que l'on veut dessiner (partie C2).
 *
 * Une vue ne recalcule rien : elle choisit des nœuds et des arcs du CallGraph. Les compteurs du
 * graphe complet (59 nœuds, 44 arcs sur le projet de validation) ne dépendent donc pas des filtres.
 *
 * @param nodes       méthodes dessinées, dans l'ordre des clés
 * @param edges       arcs dessinés, dans l'ordre du graphe
 * @param highlighted méthodes mises en évidence (celles que désigne l'option de voisinage)
 * @param byClass     regrouper les méthodes dans un cadre par classe
 */
public record GraphView(Set<String> nodes, List<CallGraph.Edge> edges, Set<String> highlighted, boolean byClass) {

    /**
     * Filtres demandés sur la ligne de commande.
     *
     * @param packageName  ne garder que les méthodes des types de ce paquetage, ou null
     * @param focus        ne garder que les méthodes dont la clé contient ce texte, leurs appelantes
     *                     et leurs appelées, ou null
     * @param hideIsolated retirer les méthodes sans aucun arc dans la vue
     * @param byClass      regrouper les méthodes par classe
     */
    public record Filter(String packageName, String focus, boolean hideIsolated, boolean byClass) {

        public static final Filter NONE = new Filter(null, null, false, false);

        public boolean isNone() {
            return equals(NONE);
        }
    }

    /**
     * @throws IllegalArgumentException si le paquetage est inconnu ou si aucune méthode ne correspond
     */
    public static GraphView of(CallGraph graph, ProjectFacts facts, Filter filter) {
        Set<String> nodes = new TreeSet<>(graph.getNodes());
        List<CallGraph.Edge> edges = new ArrayList<>(graph.getEdges());
        Set<String> highlighted = new TreeSet<>();

        // 1. Un paquetage : ses méthodes, et les arcs dont les deux extrémités y sont
        if (filter.packageName() != null) {
            if (!facts.getPackages().contains(filter.packageName())) {
                throw new IllegalArgumentException("Paquetage inconnu : « " + filter.packageName()
                        + " ». Paquetages du projet : " + String.join(", ", facts.getPackages()) + ".");
            }
            nodes.removeIf(node -> !filter.packageName().equals(packageOf(node, facts)));
            edges.removeIf(edge -> !nodes.contains(edge.source()) || !nodes.contains(edge.target()));
        }

        // 2. Le voisinage d'une méthode : elle, ses appelantes et ses appelées, et les arcs qui la touchent
        if (filter.focus() != null) {
            nodes.stream().filter(node -> node.contains(filter.focus())).forEach(highlighted::add);
            if (highlighted.isEmpty()) {
                throw new IllegalArgumentException("Aucune méthode de la vue ne contient « " + filter.focus() + " ».");
            }
            edges.removeIf(edge -> !highlighted.contains(edge.source()) && !highlighted.contains(edge.target()));
            Set<String> neighbourhood = new TreeSet<>(highlighted);
            for (CallGraph.Edge edge : edges) {
                neighbourhood.add(edge.source());
                neighbourhood.add(edge.target());
            }
            nodes.retainAll(neighbourhood);
        }

        // 3. Les méthodes sans arc : elles occupent de la place sans montrer de relation
        if (filter.hideIsolated()) {
            Set<String> connected = new TreeSet<>(highlighted);
            for (CallGraph.Edge edge : edges) {
                connected.add(edge.source());
                connected.add(edge.target());
            }
            nodes.retainAll(connected);
        }

        return new GraphView(nodes, edges, highlighted, filter.byClass());
    }

    private static String packageOf(String methodKey, ProjectFacts facts) {
        MethodFact method = facts.getMethods().get(methodKey);
        TypeFact type = method == null ? null : facts.getTypes().get(method.declaringType());
        return type == null ? null : type.packageName();
    }
}
