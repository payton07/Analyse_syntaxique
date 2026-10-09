package hai913i.tp1.analysis;

/**
 * Écrit un graphe d'appel au format DOT (Graphviz).
 *
 * L'identifiant d'un nœud est la clé complète de la méthode (unique) ; son étiquette en est une
 * forme courte, sans les noms de paquetages, pour que l'image reste lisible.
 */
public final class DotExporter {

    private DotExporter() {}

    public static String export(CallGraph graph) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph CallGraph {\n");
        sb.append("  rankdir=LR;\n");
        sb.append("  node [shape=box, fontname=\"Helvetica\", fontsize=10];\n");
        sb.append("  edge [fontname=\"Helvetica\", fontsize=9];\n\n");

        for (String node : graph.getNodes()) {
            sb.append(String.format("  \"%s\" [label=\"%s\"];%n", node, shortLabel(node)));
        }
        sb.append("\n");

        for (CallGraph.Edge edge : graph.getEdges()) {
            // Le poids n'est écrit que s'il dépasse 1 : un arc sans étiquette vaut un seul site d'appel
            String label = edge.weight() > 1 ? " [label=\"" + edge.weight() + "\"]" : "";
            sb.append(String.format("  \"%s\" -> \"%s\"%s;%n", edge.source(), edge.target(), label));
        }

        sb.append("}\n");
        return sb.toString();
    }

    // library.service.Catalog#add(library.model.Item) -> Catalog#add(Item)
    // Retire chaque segment de paquetage (identifiant en minuscule suivi d'un point).
    static String shortLabel(String methodKey) {
        return methodKey.replaceAll("\\b[a-z_][A-Za-z0-9_]*\\.", "");
    }
}
