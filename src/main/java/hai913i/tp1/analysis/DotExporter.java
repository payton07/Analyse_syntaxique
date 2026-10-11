package hai913i.tp1.analysis;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.TreeMap;

import hai913i.tp1.model.facts.MethodFact;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.model.facts.TypeFact;

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

    // Une couleur de fond par paquetage, attribuée dans l'ordre alphabétique des paquetages
    private static final List<String> PACKAGE_COLORS =
            List.of("#E3F2FD", "#E8F5E9", "#FFF3E0", "#F3E5F5", "#FFFDE7", "#E0F2F1", "#FCE4EC", "#ECEFF1");

    /**
     * Écrit une vue du graphe (C2) : seulement ses nœuds et ses arcs, avec les méthodes mises en
     * évidence, et, si la vue le demande, un cadre par classe coloré selon son paquetage.
     */
    public static String export(GraphView view, ProjectFacts facts) {
        StringBuilder sb = new StringBuilder();
        sb.append("digraph CallGraph {\n");
        sb.append("  rankdir=LR;\n");
        sb.append("  node [shape=box, style=\"rounded,filled\", fillcolor=white, fontname=\"Helvetica\", fontsize=10];\n");
        sb.append("  edge [fontname=\"Helvetica\", fontsize=9, color=\"#555555\"];\n\n");

        if (view.byClass()) {
            appendClusters(sb, view, facts);
        } else {
            for (String node : view.nodes()) {
                appendNode(sb, "  ", node, shortLabel(node), view);
            }
        }
        sb.append("\n");

        for (CallGraph.Edge edge : view.edges()) {
            String label = edge.weight() > 1 ? " [label=\"" + edge.weight() + "\"]" : "";
            sb.append(String.format("  \"%s\" -> \"%s\"%s;%n", edge.source(), edge.target(), label));
        }

        sb.append("}\n");
        return sb.toString();
    }

    // Un sous-graphe « cluster » par classe : Graphviz l'entoure d'un cadre. Dans le cadre,
    // l'étiquette d'une méthode n'a plus besoin de répéter le nom de sa classe.
    private static void appendClusters(StringBuilder sb, GraphView view, ProjectFacts facts) {
        Map<String, List<String>> nodesByType = new TreeMap<>();
        for (String node : view.nodes()) {
            nodesByType.computeIfAbsent(declaringType(node, facts), type -> new ArrayList<>()).add(node);
        }
        List<String> packages = new ArrayList<>(facts.getPackages());

        int index = 0;
        for (Map.Entry<String, List<String>> entry : nodesByType.entrySet()) {
            TypeFact type = facts.getTypes().get(entry.getKey());
            String packageName = type == null ? "" : type.packageName();
            String color = PACKAGE_COLORS.get(Math.max(0, packages.indexOf(packageName)) % PACKAGE_COLORS.size());
            String simpleType = shortLabel(entry.getKey() + "#").replace("#", "");
            // « interface » ou « énumération » devant le nom ; rien pour une classe
            String kind = (type == null || "Classe".equals(type.kind())) ? "" : "«" + type.kind().toLowerCase() + "» ";

            sb.append(String.format("  subgraph cluster_%d {%n", index++));
            sb.append(String.format("    label=\"%s%s\\n%s\";%n", kind, simpleType, packageName));
            sb.append("    fontname=\"Helvetica-Bold\"; fontsize=11; style=\"rounded,filled\";\n");
            sb.append(String.format("    color=\"#607D8B\"; fillcolor=\"%s\";%n", color));
            for (String node : entry.getValue()) {
                appendNode(sb, "    ", node, shortLabel(node.substring(node.indexOf('#') + 1)), view);
            }
            sb.append("  }\n");
        }
    }

    private static void appendNode(StringBuilder sb, String indent, String node, String label, GraphView view) {
        String emphasis = view.highlighted().contains(node) ? ", fillcolor=\"#FFD54F\", penwidth=2" : "";
        sb.append(String.format("%s\"%s\" [label=\"%s\"%s];%n", indent, node, label, emphasis));
    }

    private static String declaringType(String methodKey, ProjectFacts facts) {
        MethodFact method = facts.getMethods().get(methodKey);
        return method != null ? method.declaringType() : methodKey.substring(0, Math.max(0, methodKey.indexOf('#')));
    }

    // library.service.Catalog#add(library.model.Item) -> Catalog#add(Item)
    // Retire chaque segment de paquetage (identifiant en minuscule suivi d'un point).
    static String shortLabel(String methodKey) {
        return methodKey.replaceAll("\\b[a-z_][A-Za-z0-9_]*\\.", "");
    }
}
