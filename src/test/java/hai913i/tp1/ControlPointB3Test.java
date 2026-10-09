package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import hai913i.tp1.analysis.CallGraph;
import hai913i.tp1.analysis.DotExporter;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;
import hai913i.tp1.utils.FactBuilder;
import hai913i.tp1.visitors.CallExtractorVisitor;
import hai913i.tp1.visitors.StructureVisitor;

/**
 * Point de contrôle B3 :
 * - 59 nœuds, 44 arcs, 46 sites d'appel internes, 48 appels externes, 0 appel non résolu.
 * - Catalog#add(Item,Item) -> Catalog#add(Item) de poids 2 ; TextUtils#repeat s'appelle lui-même.
 * - Appelantes de Loanable#checkOut(Member) : LoanService#borrow seulement ;
 *   Item#checkOut(Member) n'a aucune appelante.
 * - Avec les arcs vers les constructeurs : 52 arcs et 56 sites.
 */
class ControlPointB3Test {

    private static final String CATALOG_ADD_1 = "library.service.Catalog#add(library.model.Item)";
    private static final String CATALOG_ADD_2 = "library.service.Catalog#add(library.model.Item,library.model.Item)";
    private static final String REPEAT = "library.util.TextUtils#repeat(java.lang.String,int)";
    private static final String LOANABLE_CHECK_OUT = "library.model.Loanable#checkOut(library.model.Member)";
    private static final String ITEM_CHECK_OUT = "library.model.Item#checkOut(library.model.Member)";

    private static CallGraph graph;
    private static CallGraph graphWithConstructors;

    @BeforeAll
    static void setUp() throws Exception {
        ProjectFacts facts = buildFactsFromDisk();
        graph = new CallGraph(facts, false);
        graphWithConstructors = new CallGraph(facts, true);
    }

    private static ProjectFacts buildFactsFromDisk() throws Exception {
        Path mainRoot = resolveResourcePath("validation");
        ProjectSources sources = ProjectSources.of(mainRoot);
        List<ParsedFile> files = JdtParser.parse(sources, List.of());

        StructureVisitor structVisitor = new StructureVisitor();
        CallExtractorVisitor callVisitor = new CallExtractorVisitor();

        for (ParsedFile f : files) {
            f.unit().accept(structVisitor);
            f.unit().accept(callVisitor);
        }

        return FactBuilder.build(files, structVisitor, callVisitor);
    }

    @Test
    @DisplayName("59 nœuds, 44 arcs, 46 sites internes, 48 appels externes, 0 non résolu")
    void testGlobalCounts() {
        assertAll(
                () -> assertEquals(59, graph.getNodes().size(), "59 nœuds"),
                () -> assertEquals(44, graph.getEdges().size(), "44 arcs"),
                () -> assertEquals(46, graph.getInternalCallSites(), "46 sites d'appel internes"),
                () -> assertEquals(48, graph.getExternalCalls(), "48 appels externes"),
                () -> assertEquals(0, graph.getUnresolvedCalls(), "0 appel non résolu")
        );
    }

    @Test
    @DisplayName("La somme des poids des arcs égale le nombre de sites d'appel internes")
    void testWeightsSumToInternalCallSites() {
        int totalWeight = graph.getEdges().stream().mapToInt(CallGraph.Edge::weight).sum();
        assertEquals(graph.getInternalCallSites(), totalWeight);
    }

    @Test
    @DisplayName("Surcharges : Catalog#add(Item,Item) -> Catalog#add(Item) de poids 2")
    void testOverloadsAreDistinctNodesAndEdgeIsWeighted() {
        assertTrue(graph.getNodes().containsAll(Set.of(CATALOG_ADD_1, CATALOG_ADD_2)),
                "les deux surcharges sont deux nœuds distincts");
        assertEquals(2, graph.getWeight(CATALOG_ADD_2, CATALOG_ADD_1));
    }

    @Test
    @DisplayName("Récursion : TextUtils#repeat s'appelle lui-même")
    void testRecursionIsASelfLoop() {
        assertTrue(graph.getWeight(REPEAT, REPEAT) >= 1, "arc repeat -> repeat");
        assertTrue(graph.getCallers(REPEAT).containsKey(REPEAT));
    }

    @Test
    @DisplayName("Pas d'expansion des appels virtuels : Loanable#checkOut a une appelante, Item#checkOut aucune")
    void testNoVirtualCallExpansion() {
        Set<String> loanableCallers = graph.getCallers(LOANABLE_CHECK_OUT).keySet();
        assertEquals(1, loanableCallers.size(), "une seule appelante");
        assertTrue(loanableCallers.iterator().next().startsWith("library.service.LoanService#borrow("),
                "l'appelante est LoanService#borrow");

        assertTrue(graph.getNodes().contains(ITEM_CHECK_OUT), "Item#checkOut est bien un nœud");
        assertTrue(graph.getCallers(ITEM_CHECK_OUT).isEmpty(), "Item#checkOut n'a aucune appelante");
    }

    @Test
    @DisplayName("Les deux sens sont cohérents : g est appelée de f si et seulement si f est appelante de g")
    void testCallersMirrorCallees() {
        for (CallGraph.Edge edge : graph.getEdges()) {
            assertEquals(edge.weight(), graph.getCallers(edge.target()).get(edge.source()),
                    edge.source() + " -> " + edge.target());
        }
    }

    @Test
    @DisplayName("Option arcs vers les constructeurs : 52 arcs et 56 sites, toujours 59 nœuds")
    void testConstructorArcsOption() {
        assertAll(
                () -> assertEquals(59, graphWithConstructors.getNodes().size(), "59 nœuds"),
                () -> assertEquals(52, graphWithConstructors.getEdges().size(), "52 arcs"),
                () -> assertEquals(56, graphWithConstructors.getInternalCallSites(), "56 sites")
        );
    }

    @Test
    @DisplayName("Déterminisme : deux constructions donnent les mêmes arcs et le même fichier DOT")
    void testDeterminism() throws Exception {
        CallGraph second = new CallGraph(buildFactsFromDisk(), false);
        assertEquals(graph.getEdges(), second.getEdges());
        assertEquals(DotExporter.export(graph), DotExporter.export(second));
    }

    @Test
    @DisplayName("Export DOT : un nœud par méthode, un arc par ligne, poids écrit s'il dépasse 1")
    void testDotExport() {
        String dot = DotExporter.export(graph);
        assertAll(
                () -> assertEquals(59, dot.lines().filter(l -> l.contains("[label=") && !l.contains("->")).count()),
                () -> assertEquals(44, dot.lines().filter(l -> l.contains("->")).count()),
                () -> assertTrue(dot.contains("\"" + CATALOG_ADD_2 + "\" -> \"" + CATALOG_ADD_1 + "\" [label=\"2\"];")),
                () -> assertTrue(dot.contains("[label=\"Catalog#add(Item,Item)\"]"), "étiquette courte")
        );
    }

    private static Path resolveResourcePath(String relative) {
        Path p1 = Path.of("src/test/resources", relative);
        if (Files.exists(p1)) return p1;
        Path p2 = Path.of("resources", relative);
        if (Files.exists(p2)) return p2;
        return Path.of("src/main/resources", relative);
    }
}
