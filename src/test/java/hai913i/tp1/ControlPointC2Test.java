package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assumptions.assumeTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import hai913i.tp1.analysis.CallGraph;
import hai913i.tp1.analysis.DotExporter;
import hai913i.tp1.analysis.GraphView;
import hai913i.tp1.model.facts.ProjectFacts;

/**
 * Partie C2 (bonus) : dessiner le graphe d'appel de façon lisible.
 * - Une vue choisit des nœuds et des arcs du graphe : un paquetage, le voisinage d'une méthode,
 *   sans les méthodes isolées. Elle ne change pas le graphe.
 * - L'export DOT d'une vue peut regrouper les méthodes par classe.
 * - L'image est produite par Graphviz, s'il est installé.
 */
class ControlPointC2Test {

    private static final String VALIDATION = "src/test/resources/validation";
    private static final String BORROW =
            "library.service.LoanService#borrow(library.model.Member,java.lang.String,int)";

    private static ProjectFacts facts;
    private static CallGraph graph;

    @BeforeAll
    static void analyse() throws IOException {
        facts = Analysis.of(Path.of(VALIDATION)).facts();
        graph = new CallGraph(facts, false);
    }

    private static GraphView view(String packageName, String focus, boolean hideIsolated, boolean byClass) {
        return GraphView.of(graph, facts, new GraphView.Filter(packageName, focus, hideIsolated, byClass));
    }

    // --- Vues ---

    @Test
    @DisplayName("Sans filtre, la vue est le graphe entier : 59 nœuds, 44 arcs")
    void testViewWithoutFilter() {
        GraphView all = view(null, null, false, false);
        assertEquals(graph.getNodes(), all.nodes());
        assertEquals(graph.getEdges(), all.edges());
    }

    @Test
    @DisplayName("Sans les méthodes isolées : 36 nœuds, et toujours les 44 arcs")
    void testHideIsolated() {
        GraphView connected = view(null, null, true, false);
        assertEquals(36, connected.nodes().size());
        assertEquals(44, connected.edges().size());
    }

    @Test
    @DisplayName("Un paquetage : ses 19 méthodes et les 8 arcs internes à library.service")
    void testPackageFilter() {
        GraphView service = view("library.service", null, false, false);
        assertAll(
                () -> assertEquals(19, service.nodes().size()),
                () -> assertEquals(8, service.edges().size()),
                () -> assertTrue(service.nodes().stream().allMatch(n -> n.startsWith("library.service."))),
                () -> assertTrue(service.edges().stream().allMatch(
                        e -> service.nodes().contains(e.source()) && service.nodes().contains(e.target())))
        );
    }

    @Test
    @DisplayName("Voisinage de LoanService#borrow : elle, son appelante et ses 7 appelées")
    void testNeighbourhood() {
        GraphView around = view(null, "LoanService#borrow", false, false);
        assertAll(
                () -> assertEquals(Set.of(BORROW), around.highlighted()),
                () -> assertEquals(1 + graph.getCallers(BORROW).size() + graph.getCallees(BORROW).size(),
                        around.nodes().size()),
                () -> assertEquals(9, around.nodes().size()),
                () -> assertEquals(8, around.edges().size()),
                () -> assertTrue(around.edges().stream().allMatch(
                        e -> e.source().equals(BORROW) || e.target().equals(BORROW)))
        );
    }

    @Test
    @DisplayName("Paquetage inconnu ou méthode introuvable : refus explicite")
    void testInvalidFilters() {
        assertTrue(assertThrows(IllegalArgumentException.class, () -> view("library.absent", null, false, false))
                .getMessage().contains("Paquetage inconnu"));
        assertTrue(assertThrows(IllegalArgumentException.class, () -> view(null, "Absent#rien", false, false))
                .getMessage().contains("Aucune méthode"));
    }

    // --- Export DOT d'une vue ---

    @Test
    @DisplayName("Export par classe : un cadre par classe, étiquettes sans le nom de la classe")
    void testClusteredExport() {
        String dot = DotExporter.export(view(null, null, false, true), facts);
        assertAll(
                () -> assertEquals(15, dot.lines().filter(l -> l.contains("subgraph cluster_")).count()),
                () -> assertTrue(dot.contains("label=\"LoanService.Loan\\nlibrary.service\"")),
                () -> assertTrue(dot.contains("label=\"«interface» Loanable\\nlibrary.model\"")),
                () -> assertTrue(dot.contains("\"" + BORROW + "\" [label=\"borrow(Member,String,int)\"];")),
                () -> assertEquals(44, dot.lines().filter(l -> l.contains("->")).count())
        );
    }

    @Test
    @DisplayName("La méthode du voisinage est mise en évidence ; l'export est déterministe")
    void testHighlightAndDeterminism() {
        GraphView around = view(null, "LoanService#borrow", false, true);
        String dot = DotExporter.export(around, facts);
        assertEquals(1, dot.lines().filter(l -> l.contains("penwidth=2")).count());
        assertEquals(dot, DotExporter.export(view(null, "LoanService#borrow", false, true), facts));
    }

    // --- Ligne de commande ---

    private record Run(int exitCode, String out, String err) {
    }

    private static Run run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static boolean graphvizInstalled() {
        try {
            return new ProcessBuilder("dot", "-V").redirectErrorStream(true).start().waitFor() == 0;
        } catch (IOException | InterruptedException e) {
            return false;
        }
    }

    @Test
    @DisplayName("Sans filtre, le fichier DOT est celui de B3 ; les compteurs du graphe ne changent pas")
    void testFiltersDoNotChangeTheGraph(@TempDir Path tempDir) throws IOException {
        Path plain = tempDir.resolve("plain.dot");
        Path filtered = tempDir.resolve("filtered.dot");
        Run first = run(VALIDATION, "graphe", "--dot", plain.toString());
        Run second = run(VALIDATION, "graphe", "--dot", filtered.toString(),
                "--paquetage", "library.service", "--par-classe");
        assertAll(
                () -> assertEquals(DotExporter.export(graph), Files.readString(plain)),
                () -> assertTrue(second.out().contains("Arcs                       : 44")),
                () -> assertTrue(second.out().contains("Vue dessinée : 19 nœuds sur 59, 8 arcs sur 44")),
                () -> assertFalse(first.out().contains("Vue dessinée")),
                () -> assertTrue(Files.readString(filtered).contains("subgraph cluster_"))
        );
    }

    @Test
    @DisplayName("--image : Graphviz produit un fichier SVG (test sauté si Graphviz est absent)")
    void testImage(@TempDir Path tempDir) throws IOException {
        assumeTrue(graphvizInstalled(), "Graphviz n'est pas installé");
        Path image = tempDir.resolve("voisinage.svg");
        Run run = run(VALIDATION, "graphe", "--voisinage", "LoanService#borrow", "--par-classe",
                "--image", image.toString());
        assertEquals(Main.EXIT_OK, run.exitCode(), run.err());
        assertTrue(Files.readString(image).contains("<svg"));
    }

    @Test
    @DisplayName("Filtre sans sortie, format d'image inconnu, paquetage inconnu : refus explicite")
    void testInvalidCommandLines(@TempDir Path tempDir) {
        Run noOutput = run(VALIDATION, "graphe", "--par-classe");
        Run badFormat = run(VALIDATION, "graphe", "--image", "graphe.bmp");
        Run badPackage = run(VALIDATION, "graphe", "--paquetage", "library.absent",
                "--dot", tempDir.resolve("g.dot").toString());
        assertAll(
                () -> assertEquals(Main.EXIT_USAGE, noOutput.exitCode()),
                () -> assertTrue(noOutput.err().contains("ajoutez --dot FICHIER ou --image FICHIER")),
                () -> assertEquals(Main.EXIT_USAGE, badFormat.exitCode()),
                () -> assertTrue(badFormat.err().contains("Format d'image inconnu")),
                () -> assertEquals(Main.EXIT_ANALYSIS, badPackage.exitCode()),
                () -> assertTrue(badPackage.err().contains("Paquetage inconnu")),
                () -> assertFalse(badPackage.err().contains("Exception"))
        );
    }
}
