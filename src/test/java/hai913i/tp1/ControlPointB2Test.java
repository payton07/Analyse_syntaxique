package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import hai913i.tp1.analysis.MetricsService;
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
 * Point de contrôle B2 :
 * - Q1–Q4 : 15 classes, 493 lignes, 59 méthodes, 4 paquetages.
 * - Q5–Q7 : 3,93 méth/classe, 4,64 lig/méth (246 loc pour 53 corps), 1,40 attr/classe.
 * - Q8    : Item (10), Member (6), LoanService (6) -> 3 classes pour k = 2.
 * - Q9–10 : Item et LoanService (5 attributs) ; intersection : les mêmes.
 * - Q11   : X = 5 -> 3 classes ; X = 4 -> 6 classes.
 * - Q12   : Item: checkOut (7) ; LoanService: borrow (14) ; Member: ses 6 méthodes (3 lig.) ; Loanable: aucune.
 * - Q13   : 5 paramètres -> Statistics#report.
 */
class ControlPointB2Test {

    private static MetricsService metrics;

    @BeforeAll
    static void setUp() throws Exception {
        Path mainRoot = resolveResourcePath("validation");
        ProjectSources sources = ProjectSources.of(mainRoot);
        List<JdtParser.ParsedFile> files = JdtParser.parse(sources, List.of());

        StructureVisitor structVisitor = new StructureVisitor();
        CallExtractorVisitor callVisitor = new CallExtractorVisitor();

        for (JdtParser.ParsedFile f : files) {
            f.unit().accept(structVisitor);
            f.unit().accept(callVisitor);
        }

        ProjectFacts facts = FactBuilder.build(files, structVisitor, callVisitor);
        metrics = new MetricsService(facts);
    }

    @Test
    @DisplayName("Q1 à Q4 : 15 classes, 493 lignes, 59 méthodes, 4 paquetages")
    void testQ1toQ4() {
        assertAll(
                () -> assertEquals(15, metrics.getNbClasses(), "Q1 : 15 classes (types au sens TP)"),
                () -> assertEquals(493, metrics.getTotalAppLines(), "Q2 : 493 lignes au total pour l'application"),
                () -> assertEquals(59, metrics.getTotalMethods(), "Q3 : 59 méthodes"),
                () -> assertEquals(4, metrics.getTotalPackages(), "Q4 : 4 paquetages distincts")
        );
    }

    @Test
    @DisplayName("Q5 à Q7 : moyennes 3,93 méth/classe, 4,64 lig/méth (246 lignes / 53 corps), 1,40 attr/classe")
    void testQ5toQ7() {
        assertAll(
                () -> assertEquals(3.93, metrics.getAvgMethodsPerClass(), 0.01, "Q5 : 3,93 méthodes par classe"),
                () -> assertEquals(53, metrics.getNbMethodsWithBody(), "53 méthodes concrètes avec corps"),
                () -> assertEquals(246, metrics.getTotalLinesOfMethodsWithBody(), "246 lignes totales pour les corps"),
                () -> assertEquals(4.64, metrics.getAvgLinesPerMethodWithBody(), 0.01, "Q6 : 4,64 lignes par méthode"),
                () -> assertEquals(1.40, metrics.getAvgAttributesPerClass(), 0.01, "Q7 : 1,40 attribut par classe")
        );
    }

    @Test
    @DisplayName("Q8 : 10 % classes avec le plus de méthodes -> Item (10), Member (6), LoanService (6) = 3 classes")
    void testQ8() {
        List<TypeFact> top = metrics.getTop10PercentClassesByMethods();
        List<String> simpleNames = top.stream().map(t -> getSimpleName(t.qualifiedName())).toList();

        assertEquals(3, top.size(), "k = ceil(15/10) = 2, avec ex-æquo au seuil de 6 donne 3 classes");
        assertTrue(simpleNames.contains("Item"), "Item doit être présent (10 méthodes)");
        assertTrue(simpleNames.contains("Member"), "Member doit être présent (6 méthodes)");
        assertTrue(simpleNames.contains("LoanService"), "LoanService doit être présent (6 méthodes)");
    }

    @Test
    @DisplayName("Q9 & Q10 : 10 % classes avec le plus d'attributs et intersection -> Item et LoanService (5 attributs)")
    void testQ9andQ10() {
        List<TypeFact> topAttr = metrics.getTop10PercentClassesByAttributes();
        List<String> attrNames = topAttr.stream().map(t -> getSimpleName(t.qualifiedName())).toList();

        assertEquals(2, topAttr.size(), "2 classes pour k = 2");
        assertTrue(attrNames.contains("Item") && attrNames.contains("LoanService"));

        List<TypeFact> intersection = metrics.getIntersectionTopClasses();
        List<String> interNames = intersection.stream().map(t -> getSimpleName(t.qualifiedName())).toList();

        assertEquals(2, intersection.size(), "L'intersection doit contenir exactement les 2 mêmes classes");
        assertTrue(interNames.contains("Item") && interNames.contains("LoanService"));
    }

    @Test
    @DisplayName("Q11 : Classes avec strictement plus de X méthodes (X=5 -> 3 classes ; X=4 -> 6 classes)")
    void testQ11() {
        List<TypeFact> moreThan5 = metrics.getClassesWithMoreThanXMethods(5);
        assertEquals(3, moreThan5.size(), "Pour X = 5 : Item, Member, LoanService");

        List<TypeFact> moreThan4 = metrics.getClassesWithMoreThanXMethods(4);
        assertEquals(6, moreThan4.size(), "Pour X = 4 : 6 classes");
    }

    @Test
    @DisplayName("Q12 : 10 % méthodes les plus longues par classe (Item, LoanService, Member, Loanable)")
    void testQ12() {
        Map<String, List<MethodFact>> topMethodsByClass = metrics.getTop10PercentMethodsByClass();

        // 1. Item : checkOut (7 lignes)
        List<MethodFact> itemMethods = findMethodsFor(topMethodsByClass, "Item");
        assertTrue(itemMethods.stream().anyMatch(m -> "checkOut".equals(m.simpleName()) && m.linesOfCode() == 7),
                "Item doit retenir checkOut (7 lignes)");

        // 2. LoanService : borrow (14 lignes)
        List<MethodFact> loanMethods = findMethodsFor(topMethodsByClass, "LoanService");
        assertTrue(loanMethods.stream().anyMatch(m -> "borrow".equals(m.simpleName()) && m.linesOfCode() == 14),
                "LoanService doit retenir borrow (14 lignes)");

        // 3. Member : ses 6 méthodes (toutes à 3 lignes)
        List<MethodFact> memberMethods = findMethodsFor(topMethodsByClass, "Member");
        assertEquals(6, memberMethods.size(),
                "Member doit retenir l'ensemble de ses 6 méthodes car toutes ont 3 lignes (ex-æquo)");

        // 4. Loanable : interface -> aucune méthode avec corps > 0
        List<MethodFact> loanableMethods = findMethodsFor(topMethodsByClass, "Loanable");
        assertTrue(loanableMethods.isEmpty(),
                "Loanable ne doit avoir aucune méthode retenue (règle : 0 n'est jamais retenu)");
    }

    @Test
    @DisplayName("Q13 : Nombre maximal de paramètres = 5 (Statistics#report)")
    void testQ13() {
        assertEquals(5, metrics.getMaxParameters(), "Le nombre maximal de paramètres parmi les méthodes est 5");

        List<MethodFact> maxMethods = metrics.getMethodsWithMaxParameters();
        assertEquals(1, maxMethods.size(), "Une seule méthode possède 5 paramètres");
        assertTrue(maxMethods.get(0).methodKey().contains("Statistics#report"),
                "La méthode concernée est Statistics#report");
    }

    // --- Utilitaires ---

    private static String getSimpleName(String qualifiedName) {
        return qualifiedName.contains(".") ? qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1) : qualifiedName;
    }

    private static List<MethodFact> findMethodsFor(Map<String, List<MethodFact>> map, String simpleClassName) {
        return map.entrySet().stream()
                .filter(e -> e.getKey().endsWith("." + simpleClassName) || e.getKey().equals(simpleClassName))
                .map(Map.Entry::getValue)
                .findFirst()
                .orElse(List.of());
    }

    private static Path resolveResourcePath(String relative) {
        Path p1 = Path.of("src/test/resources", relative);
        if (Files.exists(p1)) return p1;
        Path p2 = Path.of("resources", relative);
        if (Files.exists(p2)) return p2;
        return Path.of("src/main/resources", relative);
    }
}