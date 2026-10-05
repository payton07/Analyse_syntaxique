package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import hai913i.tp1.model.facts.CallFact;
import hai913i.tp1.model.facts.MethodFact;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.model.facts.TypeFact;
import hai913i.tp1.utils.FactBuilder;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;
import hai913i.tp1.visitors.CallExtractorVisitor;
import hai913i.tp1.visitors.StructureVisitor;

/**
 * Point de contrôle B1 :
 * - Les chiffres de A2 et A3 sont retrouvés à partir du modèle ProjectFacts,
 *   sans réanalyser le code.
 * - Deux exécutions successives donnent une sortie identique (déterminisme).
 * - Les appels d'une classe vers un autre fichier (ex: LoanService -> Catalog)
 *   sont bien résolus dans le modèle.
 */
class ControlPointB1Test {

    private static ProjectFacts facts;

    @BeforeAll
    static void setUp() throws Exception {
        facts = buildFactsFromDisk();
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

        return FactBuilder.build(structVisitor, callVisitor);
    }

    @Test
    @DisplayName("Point de contrôle B1 : recalcul exact des métriques structurelles de A2")
    void testA2MetricsReconstructedFromFacts() {
        assertEquals(15, facts.getTypes().size(), "Attendu : 15 types au total");

        long interfaceCount = facts.getTypes().values().stream()
                .filter(TypeFact::isInterface)
                .count();

        long enumCount = facts.getTypes().values().stream()
                .filter(TypeFact::isEnum)
                .count();

        long classCount = facts.getTypes().size() - interfaceCount - enumCount;

        int totalMethods = facts.getMethods().size();
        long totalConstructors = facts.getMethods().values().stream()
                .filter(MethodFact::isConstructor)
                .count();

        int totalFields = facts.getTypes().values().stream()
                .mapToInt(TypeFact::getFieldCount)
                .sum();

        assertAll(
                () -> assertEquals(12, classCount, "12 classes ordinaires"),
                () -> assertEquals(2, interfaceCount, "2 interfaces"),
                () -> assertEquals(1, enumCount, "1 énumération"),
                () -> assertEquals(59, totalMethods, "59 méthodes au total"),
                () -> assertEquals(9, totalConstructors, "9 constructeurs"),
                () -> assertEquals(21, totalFields, "21 attributs au total")
        );
    }

    @Test
    @DisplayName("Point de contrôle B1 : recalcul exact des métriques d'appels de A3")
    void testA3MetricsReconstructedFromFacts() {
        List<CallFact> allCalls = facts.getAllCalls();
        assertEquals(94, allCalls.size(), "Attendu : 94 appels au total");

        long unresolved = allCalls.stream()
                .filter(c -> !c.isResolved())
                .count();

        long internal = allCalls.stream()
                .filter(c -> facts.isInternalMethod(c.resolvedTarget()))
                .count();

        long external = allCalls.size() - internal - unresolved;

        assertAll(
                () -> assertEquals(0, unresolved, "0 appel non résolu"),
                () -> assertEquals(46, internal, "46 appels vers le projet"),
                () -> assertEquals(48, external, "48 appels vers l'extérieur")
        );
    }

    @Test
    @DisplayName("Point de contrôle B1 : résolution des appels inter-fichiers (LoanService -> Catalog)")
    void testInterFileCallsResolvedInFacts() {
        // Recherche des appels émis depuis les méthodes de LoanService
        List<CallFact> loanServiceCalls = facts.getMethods().values().stream()
                .filter(m -> m.declaringType().endsWith("LoanService"))
                .flatMap(m -> m.calls().stream())
                .toList();

        boolean catalogCallResolved = loanServiceCalls.stream()
                .anyMatch(c -> c.isResolved() && c.resolvedTarget().startsWith("library.service.Catalog#"));

        assertTrue(catalogCallResolved,
                "L'appel depuis LoanService vers Catalog doit être résolu dans le modèle de faits");
    }

    @Test
    @DisplayName("Point de contrôle B1 : déterminisme et reproductibilité stricte")
    void testDeterminismBetweenRuns() throws Exception {
        ProjectFacts secondRun = buildFactsFromDisk();

        // 1. Ordre et contenu identiques des types
        List<String> firstRunTypes = List.copyOf(facts.getTypes().keySet());
        List<String> secondRunTypes = List.copyOf(secondRun.getTypes().keySet());
        assertEquals(firstRunTypes, secondRunTypes, "L'ordre et les clés des types doivent être strictement identiques");

        // 2. Ordre et contenu identiques des méthodes
        List<String> firstRunMethods = List.copyOf(facts.getMethods().keySet());
        List<String> secondRunMethods = List.copyOf(secondRun.getMethods().keySet());
        assertEquals(firstRunMethods, secondRunMethods, "L'ordre et les clés des méthodes doivent être strictement identiques");

        // 3. Ordre et contenu identiques des appels
        assertEquals(facts.getAllCalls(), secondRun.getAllCalls(),
                "La liste ordonnée des appels extraits doit être rigoureusement identique");
    }

    private static Path resolveResourcePath(String relative) {
        Path p1 = Path.of("src/test/resources", relative);
        if (Files.exists(p1)) return p1;
        Path p2 = Path.of("resources", relative);
        if (Files.exists(p2)) return p2;
        return Path.of("src/main/resources", relative);
    }
}