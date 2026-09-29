package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.Set;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import hai913i.tp1.model.infos.CallSiteInfo;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;
import hai913i.tp1.visitors.CallExtractorVisitor;

/**
 * Point de contrôle A3 :
 * - 94 appels au total (46 internes, 48 externes, 0 non résolu).
 * - Types statiques des receveurs et cibles résolues spécifiques (§ 4.4).
 * - Résistance aux dépendances manquantes (missing-dep).
 */
class ControlPointA3Test {

    private static Map<String, List<CallSiteInfo>> callsByMethod;
    private static Set<String> projectMethodKeys;
    private static List<CallSiteInfo> allCalls;

    @BeforeAll
    static void setUp() throws Exception {
        Path mainRoot = resolveResourcePath("validation");
        ProjectSources sources = ProjectSources.of(mainRoot);
        List<ParsedFile> files = JdtParser.parse(sources, List.of());

        CallExtractorVisitor visitor = new CallExtractorVisitor();
        for (ParsedFile file : files) {
            file.unit().accept(visitor);
        }

        callsByMethod = visitor.getCallsByMethod();
        projectMethodKeys = callsByMethod.keySet();
        allCalls = callsByMethod.values().stream().flatMap(List::stream).toList();
    }

    @Test
    @DisplayName("94 appels : 46 internes, 48 externes, 0 non résolu")
    void testGlobalCallCounts() {
        assertEquals(94, allCalls.size(), "Le total d'appels doit être exactement de 94");

        long unresolvedCount = allCalls.stream()
                .filter(c -> "non résolue".equals(c.resolvedTarget))
                .count();

        long internalCount = allCalls.stream()
                .filter(c -> !"non résolue".equals(c.resolvedTarget) && projectMethodKeys.contains(c.resolvedTarget))
                .count();

        long externalCount = allCalls.stream()
                .filter(c -> !"non résolue".equals(c.resolvedTarget) && !projectMethodKeys.contains(c.resolvedTarget))
                .count();

        assertAll(
                () -> assertEquals(0, unresolvedCount, "Aucun appel ne doit être non résolu sur le projet principal"),
                () -> assertEquals(46, internalCount, "Attendu : 46 appels vers le projet"),
                () -> assertEquals(48, externalCount, "Attendu : 48 appels vers l'extérieur")
        );
    }

    @Test
    @DisplayName("LoanService.borrow (ligne 36) : loanable.checkOut(member) a le receveur Loanable")
    void testReceiverIsDeclaredStaticType() {
        String callerKey = findMethodKey("LoanService", "borrow");
        assertNotNull(callerKey, "La méthode LoanService#borrow doit exister");

        CallSiteInfo call = findCall(callerKey, "checkOut", 36);
        assertNotNull(call, "L'appel loanable.checkOut(member) à la ligne 36 doit être trouvé");
        assertEquals("library.model.Loanable", call.receiverStaticType,
                "Le receveur doit être le type statique déclaré (Loanable), pas Item");
    }

    @Test
    @DisplayName("LibraryApp.main (ligne 32) : zadig.getTitle() a le receveur Book et la cible Item#getTitle()")
    void testInheritedMethodCallTarget() {
        String callerKey = findMethodKey("LibraryApp", "main");
        assertNotNull(callerKey, "La méthode LibraryApp#main doit exister");

        CallSiteInfo call = findCall(callerKey, "getTitle", 32);
        assertNotNull(call, "L'appel zadig.getTitle() à la ligne 32 doit être trouvé");
        assertEquals("library.model.Book", call.receiverStaticType, "Le receveur statique doit être Book");
        assertEquals("library.model.Item#getTitle()", call.resolvedTarget,
                "La cible doit être la classe déclarante d'origine (Item)");
    }

    @Test
    @DisplayName("Book.describe (ligne 32) : super.describe() a le receveur Item")
    void testSuperMethodInvocationReceiver() {
        String callerKey = findMethodKey("Book", "describe");
        assertNotNull(callerKey, "La méthode Book#describe doit exister");

        CallSiteInfo call = findCall(callerKey, "describe", 32);
        assertNotNull(call, "L'appel super.describe() à la ligne 32 doit être trouvé");
        assertEquals("library.model.Item", call.receiverStaticType,
                "Le receveur de super.describe() doit être la superclasse directe (Item)");
    }

    @Test
    @DisplayName("Catalog.available (ligne 37) : item.isAvailable() dans lambda a le receveur Item")
    void testLambdaCallReceiver() {
        String callerKey = findMethodKey("Catalog", "available");
        assertNotNull(callerKey, "La méthode Catalog#available doit exister");

        CallSiteInfo call = findCall(callerKey, "isAvailable", 37);
        assertNotNull(call, "L'appel item.isAvailable() à la ligne 37 doit être trouvé");
        assertEquals("library.model.Item", call.receiverStaticType,
                "Le receveur doit être Item même au sein d'une lambda");
    }

    @Test
    @DisplayName("Robustesse (missing-dep) : StringUtils non résolu, formatter résolu, analyse complète")
    void testRobustnessMissingDependency() throws Exception {
        Path missingDepPath = resolveResourcePath("robustness/missing-dep");
        if (!Files.exists(missingDepPath)) {
            return;
        }

        ProjectSources sources = ProjectSources.of(missingDepPath);
        List<ParsedFile> files = JdtParser.parse(sources, List.of());

        CallExtractorVisitor visitor = new CallExtractorVisitor();
        assertDoesNotThrow(() -> {
            for (ParsedFile f : files) {
                f.unit().accept(visitor);
            }
        }, "L'analyse ne doit pas planter sur un projet avec dépendances manquantes");

        List<CallSiteInfo> robustnessCalls = visitor.getCallsByMethod().values().stream()
                .flatMap(List::stream)
                .toList();

        boolean hasUnresolvedCapitalize = robustnessCalls.stream()
                .anyMatch(c -> "capitalize".equals(c.methodName) && "non résolue".equals(c.resolvedTarget));
        assertTrue(hasUnresolvedCapitalize, "L'appel à StringUtils.capitalize doit être non résolu");

        boolean hasResolvedDecorate = robustnessCalls.stream()
                .anyMatch(c -> "decorate".equals(c.methodName) && !"non résolue".equals(c.resolvedTarget));
        assertTrue(hasResolvedDecorate, "L'appel à formatter.decorate doit être résolu");
    }

    // --- Utilitaires de recherche pour les assertions ---

    private static String findMethodKey(String simpleClassName, String methodName) {
        return projectMethodKeys.stream()
                .filter(k -> k.contains("." + simpleClassName + "#" + methodName + "("))
                .findFirst()
                .orElse(null);
    }

    private static CallSiteInfo findCall(String callerKey, String calleeName, int lineNumber) {
        List<CallSiteInfo> calls = callsByMethod.get(callerKey);
        if (calls == null) return null;
        return calls.stream()
                .filter(c -> calleeName.equals(c.methodName) && c.lineNumber == lineNumber)
                .findFirst()
                .orElse(null);
    }

    private static Path resolveResourcePath(String relative) {
        Path p1 = Path.of("src/test/resources", relative);
        if (Files.exists(p1)) return p1;
        Path p2 = Path.of("resources", relative);
        if (Files.exists(p2)) return p2;
        return Path.of("src/main/resources", relative);
    }
}