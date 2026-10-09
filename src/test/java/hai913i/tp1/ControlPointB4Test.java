package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.stream.Stream;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

/**
 * Point de contrôle B4 : la ligne de commande, de bout en bout, par Main.run.
 * - no-java-files et chemin inexistant : message clair, code de sortie non nul.
 * - syntax-error : erreurs de Broken.java signalées, Helper.java analysé, l'outil ne s'arrête pas.
 * - X = abc : refus explicite.
 * - Aucune pile d'exception brute ; chemin contenant des espaces accepté.
 */
class ControlPointB4Test {

    private static final String VALIDATION = "src/test/resources/validation";
    private static final String ROBUSTNESS = "src/test/resources/robustness/";

    /** Résultat d'une exécution : code de sortie, sortie standard, sortie d'erreur. */
    private record Run(int exitCode, String out, String err) {
    }

    private static Run run(String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(args,
                new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        return new Run(exitCode, out.toString(StandardCharsets.UTF_8), err.toString(StandardCharsets.UTF_8));
    }

    private static void assertCleanError(Run run, int expectedExitCode, String expectedMessage) {
        assertAll(
                () -> assertEquals(expectedExitCode, run.exitCode(), "code de sortie"),
                () -> assertTrue(run.err().contains(expectedMessage), "message attendu, obtenu : " + run.err()),
                () -> assertFalse(run.err().contains("\tat "), "aucune pile d'exception brute"),
                () -> assertFalse(run.err().contains("Exception"), "aucun nom d'exception brut")
        );
    }

    // --- Analyse impossible : code 3 ---

    @Test
    @DisplayName("Chemin inexistant : message clair, code de sortie non nul")
    void testMissingDirectory() {
        assertCleanError(run("dossier/qui/n/existe/pas", "structure"), Main.EXIT_ANALYSIS, "Dossier introuvable");
    }

    @Test
    @DisplayName("robustness/no-java-files : message clair, code de sortie non nul")
    void testNoJavaFiles() {
        assertCleanError(run(ROBUSTNESS + "no-java-files", "structure"), Main.EXIT_ANALYSIS, "Aucun fichier .java");
    }

    @Test
    @DisplayName("--fichier inconnu : message clair, code de sortie non nul")
    void testUnknownFileForAst() {
        assertCleanError(run(VALIDATION, "ast", "--fichier", "Absent.java"), Main.EXIT_ANALYSIS, "Absent.java");
    }

    // --- Arguments invalides : code 2 ---

    @Test
    @DisplayName("X = abc : refus explicite")
    void testMalformedX() {
        assertCleanError(run(VALIDATION, "metriques", "--x", "abc"), Main.EXIT_USAGE, "Seuil X invalide : « abc »");
    }

    @Test
    @DisplayName("X négatif, X absent, X sans valeur : refus explicite")
    void testNegativeOrMissingX() {
        assertCleanError(run(VALIDATION, "metriques", "--x", "-1"), Main.EXIT_USAGE, "Seuil X invalide");
        assertCleanError(run(VALIDATION, "metriques"), Main.EXIT_USAGE, "Le seuil X est obligatoire");
        assertCleanError(run(VALIDATION, "metriques", "--x"), Main.EXIT_USAGE, "attend une valeur");
    }

    @Test
    @DisplayName("Sans argument, action ou option inconnue : message d'usage")
    void testUsageErrors() {
        assertCleanError(run(), Main.EXIT_USAGE, "Usage :");
        assertCleanError(run(VALIDATION, "danse"), Main.EXIT_USAGE, "Action inconnue");
        assertCleanError(run(VALIDATION, "graphe", "--couleur"), Main.EXIT_USAGE, "Option inconnue");
    }

    @Test
    @DisplayName("--help : usage sur la sortie standard, code 0")
    void testHelp() {
        Run run = run("--help");
        assertEquals(Main.EXIT_OK, run.exitCode());
        assertTrue(run.out().contains("Usage :"));
    }

    // --- Robustesse : l'analyse va au bout ---

    @Test
    @DisplayName("robustness/syntax-error : erreurs signalées, Helper.java analysé, l'outil ne s'arrête pas")
    void testSyntaxError() {
        Run run = run(ROBUSTNESS + "syntax-error", "structure");
        assertAll(
                () -> assertEquals(Main.EXIT_OK, run.exitCode()),
                () -> assertTrue(run.out().contains("Erreurs de compilation : 2")),
                () -> assertTrue(run.out().contains("Broken.java:"), "les erreurs de Broken.java sont signalées"),
                () -> assertTrue(run.out().contains("Classe : demo.Helper"), "Helper.java est analysé")
        );
    }

    @Test
    @DisplayName("robustness/missing-dep : un appel non résolu signalé, l'autre résolu")
    void testMissingDependency() {
        Run run = run(ROBUSTNESS + "missing-dep", "appels");
        assertAll(
                () -> assertEquals(Main.EXIT_OK, run.exitCode()),
                () -> assertTrue(run.out().contains("capitalize | Receveur : <type non résolu> | Cible : non résolue")),
                () -> assertTrue(run.out().contains("Cible : demo.Formatter#decorate(java.lang.String)")),
                () -> assertTrue(run.out().contains("Non résolus      : 1"))
        );
    }

    // --- Fonctionnement normal ---

    @Test
    @DisplayName("Chemin contenant des espaces")
    void testPathWithSpaces(@TempDir Path tempDir) throws IOException {
        Path project = tempDir.resolve("mon projet avec espaces");
        copyDirectory(Path.of("src/test/resources/sample"), project);

        Run run = run(project.toString(), "faits");
        assertEquals(Main.EXIT_OK, run.exitCode(), run.err());
        assertTrue(run.out().contains("Unites de compilation  : 2"));
    }

    @Test
    @DisplayName("Le seuil X de la ligne de commande est appliqué (X = 5 : 3 classes ; X = 4 : 6 classes)")
    void testXFromCommandLine() {
        assertTrue(run(VALIDATION, "metriques", "--x", "5").out().contains("plus de X = 5 méthodes (3 trouvées)"));
        assertTrue(run(VALIDATION, "metriques", "--x", "4").out().contains("plus de X = 4 méthodes (6 trouvées)"));
    }

    @Test
    @DisplayName("graphe : compteurs, interrogation d'une méthode et fichier DOT")
    void testGraphAction(@TempDir Path tempDir) throws IOException {
        Path dot = tempDir.resolve("callgraph.dot");
        Run run = run(VALIDATION, "graphe", "--methode", "Loanable#checkOut", "--dot", dot.toString());

        assertAll(
                () -> assertEquals(Main.EXIT_OK, run.exitCode()),
                () -> assertTrue(run.out().contains("Arcs                       : 44")),
                () -> assertTrue(run.out().contains("<- library.service.LoanService#borrow(")),
                () -> assertTrue(Files.readString(dot).startsWith("digraph CallGraph {"))
        );
    }

    @Test
    @DisplayName("tout : deux exécutions donnent exactement la même sortie")
    void testWholeOutputIsDeterministic() {
        Run first = run(VALIDATION, "tout", "--x", "5");
        Run second = run(VALIDATION, "tout", "--x", "5");
        assertEquals(Main.EXIT_OK, first.exitCode());
        assertEquals(first.out(), second.out());
    }

    private static void copyDirectory(Path source, Path target) throws IOException {
        try (Stream<Path> paths = Files.walk(source)) {
            for (Path path : (Iterable<Path>) paths::iterator) {
                Path destination = target.resolve(source.relativize(path).toString());
                if (Files.isDirectory(path)) {
                    Files.createDirectories(destination);
                } else {
                    Files.copy(path, destination);
                }
            }
        }
    }
}
