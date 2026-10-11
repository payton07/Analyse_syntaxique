package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.PrintStream;
import java.nio.charset.StandardCharsets;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import hai913i.tp1.analysis.CallGraph;
import hai913i.tp1.cli.Options;
import hai913i.tp1.model.facts.CallFact;
import hai913i.tp1.model.facts.MethodFact;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.model.facts.TypeFact;
import hai913i.tp1.spoon.EngineComparison;
import hai913i.tp1.spoon.FactComparison;

/**
 * Partie C1 (bonus) : la même extraction refaite avec Spoon, sur le même modèle de faits.
 * - Spoon brut : les chiffres de l'annexe « Eclipse JDT et Spoon », § 2.4.
 * - Spoon avec les conventions du § 4 : le même modèle de faits que JDT, donc le même graphe.
 */
class ControlPointC1Test {

    private static final String VALIDATION = "src/test/resources/validation";
    private static final String ROBUSTNESS = "src/test/resources/robustness/";

    private static ProjectFacts jdt;
    private static ProjectFacts spoon;
    private static EngineComparison comparison;

    @BeforeAll
    static void analyse() throws IOException {
        Analysis analysis = Analysis.of(Path.of(VALIDATION));
        jdt = analysis.facts();
        spoon = Analysis.of(Path.of(VALIDATION), Options.SPOON).facts();
        comparison = EngineComparison.of(analysis.sources(), jdt);
    }

    @Test
    @DisplayName("Spoon brut : les comptages de l'annexe 2")
    void testRawSpoonCounts() {
        EngineComparison.RawCounts raw = comparison.raw();
        assertAll(
                () -> assertEquals(14, raw.topLevelTypes(), "getAllTypes() : premier niveau seulement"),
                () -> assertEquals(15, raw.allTypes(), "tous les CtType"),
                () -> assertEquals(50, raw.methods(), "CtMethod : sans les constructeurs"),
                () -> assertEquals(13, raw.constructors(), "CtConstructor"),
                () -> assertEquals(4, raw.implicitConstructors(), "constructeurs par défaut ajoutés"),
                () -> assertEquals(24, raw.fields(), "CtField"),
                () -> assertEquals(3, raw.enumValues(), "constantes de Category"),
                () -> assertEquals(6, raw.packages(), "getAllPackages()"),
                () -> assertEquals(107, raw.invocations(), "CtInvocation"),
                () -> assertEquals(13, raw.constructorInvocations(), "this(...) et super(...)")
        );
    }

    @Test
    @DisplayName("Spoon avec les conventions du sujet : 15 types, 59 méthodes, 21 attributs, 94 appels")
    void testSpoonFactsFollowConventions() {
        List<CallFact> calls = spoon.getAllCalls();
        long internal = calls.stream().filter(c -> spoon.isInternalMethod(c.resolvedTarget())).count();
        assertAll(
                () -> assertEquals(15, spoon.getTypes().size()),
                () -> assertEquals(59, spoon.getMethods().size()),
                () -> assertEquals(9, spoon.getMethods().values().stream().filter(MethodFact::isConstructor).count()),
                () -> assertEquals(21, spoon.getTypes().values().stream().mapToInt(TypeFact::getFieldCount).sum()),
                () -> assertEquals(4, spoon.getPackages().size()),
                () -> assertEquals(94, calls.size()),
                () -> assertEquals(46, internal),
                () -> assertEquals(0, calls.stream().filter(c -> !c.isResolved()).count()),
                () -> assertTrue(spoon.getTypes().containsKey("library.service.LoanService.Loan"),
                        "type imbriqué nommé avec un point, pas avec $")
        );
    }

    @Test
    @DisplayName("Les deux modèles de faits sont identiques, entrée par entrée")
    void testSameFactsAsJdt() {
        assertEquals(List.of(), FactComparison.differences(jdt, spoon));
    }

    @Test
    @DisplayName("Graphe d'appel tiré de Spoon : 59 nœuds, 44 arcs, 46 sites ; mêmes arcs avec les constructeurs")
    void testSameCallGraph() {
        CallGraph graph = new CallGraph(spoon, false);
        assertAll(
                () -> assertEquals(59, graph.getNodes().size()),
                () -> assertEquals(44, graph.getEdges().size()),
                () -> assertEquals(46, graph.getInternalCallSites()),
                () -> assertEquals(new CallGraph(jdt, false).getEdges(), graph.getEdges()),
                () -> assertEquals(new CallGraph(jdt, true).getEdges(), new CallGraph(spoon, true).getEdges())
        );
    }

    @Test
    @DisplayName("FactComparison signale un écart quand les modèles diffèrent")
    void testComparisonDetectsDifferences() throws IOException {
        ProjectFacts sample = Analysis.of(Path.of("src/test/resources/sample")).facts();
        assertFalse(FactComparison.differences(jdt, sample).isEmpty());
    }

    // --- Mode noClasspath et robustesse ---

    @Test
    @DisplayName("missing-dep : la cible devinée par Spoon n'est pas déclarée résolue")
    void testGuessedTargetIsNotResolved() throws IOException {
        Analysis jdtAnalysis = Analysis.of(Path.of(ROBUSTNESS + "missing-dep"));
        EngineComparison missingDep = EngineComparison.of(jdtAnalysis.sources(), jdtAnalysis.facts());
        assertAll(
                () -> assertEquals(List.of("Greeter.java:14 org.apache.commons.lang3.StringUtils#capitalize"),
                        missingDep.guessedTargets(), "Spoon propose StringUtils d'après l'import"),
                () -> assertEquals(1, missingDep.spoonFacts().getAllCalls().stream()
                        .filter(c -> !c.isResolved()).count(), "l'appel reste non résolu dans le modèle"),
                () -> assertEquals(List.of(), missingDep.differences())
        );
    }

    @Test
    @DisplayName("syntax-error : Spoon construit un modèle, identique à celui de JDT")
    void testSyntaxError() throws IOException {
        Analysis jdtAnalysis = Analysis.of(Path.of(ROBUSTNESS + "syntax-error"));
        assertEquals(List.of(), EngineComparison.of(jdtAnalysis.sources(), jdtAnalysis.facts()).differences());
    }

    // --- Ligne de commande ---

    private static String run(int expectedExitCode, String... args) {
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ByteArrayOutputStream err = new ByteArrayOutputStream();
        int exitCode = Main.run(args, new PrintStream(out, true, StandardCharsets.UTF_8),
                new PrintStream(err, true, StandardCharsets.UTF_8));
        assertEquals(expectedExitCode, exitCode, err.toString(StandardCharsets.UTF_8));
        return out.toString(StandardCharsets.UTF_8) + err.toString(StandardCharsets.UTF_8);
    }

    // L'en-tête diffère d'un moteur à l'autre : on compare ce qui suit le titre de l'action
    private static String body(String output) {
        return output.substring(output.indexOf("=========="));
    }

    @Test
    @DisplayName("--moteur spoon : appels, métriques et graphe donnent la même sortie qu'avec JDT")
    void testEngineOption() {
        assertAll(
                () -> assertEquals(body(run(Main.EXIT_OK, VALIDATION, "appels")),
                        body(run(Main.EXIT_OK, VALIDATION, "appels", "--moteur", "spoon"))),
                () -> assertEquals(body(run(Main.EXIT_OK, VALIDATION, "metriques", "--x", "5")),
                        body(run(Main.EXIT_OK, VALIDATION, "metriques", "--x", "5", "--moteur", "spoon"))),
                () -> assertEquals(body(run(Main.EXIT_OK, VALIDATION, "graphe", "--constructeurs")),
                        body(run(Main.EXIT_OK, VALIDATION, "graphe", "--constructeurs", "--moteur", "spoon"))),
                () -> assertTrue(run(Main.EXIT_OK, VALIDATION, "structure", "--moteur", "spoon")
                        .contains("Énumération : library.model.Category"))
        );
    }

    @Test
    @DisplayName("comparer : tableau des comptages et aucun écart sur le projet de validation")
    void testCompareAction() {
        String output = run(Main.EXIT_OK, VALIDATION, "comparer");
        assertAll(
                () -> assertTrue(output.contains("50 + 13 constr. (4 impl.)")),
                () -> assertTrue(output.contains("59 / 44 / 46")),
                () -> assertTrue(output.contains("Écarts entre les deux modèles de faits : 0"))
        );
    }

    @Test
    @DisplayName("Moteur inconnu, ou ast avec Spoon : refus explicite")
    void testInvalidEngine() {
        assertTrue(run(Main.EXIT_USAGE, VALIDATION, "structure", "--moteur", "antlr").contains("Moteur inconnu"));
        assertTrue(run(Main.EXIT_USAGE, VALIDATION, "ast", "--moteur", "spoon").contains("n'existe pas avec --moteur spoon"));
    }
}
