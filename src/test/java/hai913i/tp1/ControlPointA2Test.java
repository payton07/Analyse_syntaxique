package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import hai913i.tp1.model.infos.ClassInfo;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;
import hai913i.tp1.visitors.StructureVisitor;

/**
 * Point de contrôle A2 :
 * - 15 types : 12 classes, 2 interfaces, 1 énumération.
 * - 59 méthodes (dont 9 constructeurs) et 21 attributs au total.
 * - Métriques détaillées sur LoanService et LoanService.Loan.
 * - Superclasses de LoanException : Exception et Throwable.
 * - Visibilités des attributs de library.model.Item.
 */
class ControlPointA2Test {

    private static List<ClassInfo> classes;
    private static Map<String, ClassInfo> classByName;

    @BeforeAll
    static void setUp() throws Exception {
        Path mainRoot = resolveResourcePath("validation");
        ProjectSources sources = ProjectSources.of(mainRoot);
        List<ParsedFile> files = JdtParser.parse(sources, List.of());

        StructureVisitor visitor = new StructureVisitor();
        for (ParsedFile file : files) {
            file.unit().accept(visitor);
        }

        classes = visitor.getClasses();
        classByName = classes.stream()
                .collect(Collectors.toMap(c -> c.qualifiedName, c -> c));
    }

    @Test
    @DisplayName("Comptages globaux : 15 types, 59 méthodes (9 constructeurs), 21 attributs")
    void testGlobalCounts() {
        assertEquals(15, classes.size(), "Total de types attendus (classes, interfaces, enums)");

        long interfaceCount = classes.stream().filter(c -> "Interface".equals(c.kind)).count();
        long enumCount = classes.stream().filter(c -> "Énumération".equals(c.kind)).count();
        long regularClassCount = classes.stream().filter(c -> "Classe".equals(c.kind)).count();

        int totalMethods = classes.stream().mapToInt(c -> c.methods.size()).sum();
        long totalConstructors = classes.stream()
                .flatMap(c -> c.methods.stream())
                .filter(m -> m.isConstructor)
                .count();

        int totalAttributes = classes.stream().mapToInt(c -> c.fields.size()).sum();

        assertAll(
                () -> assertEquals(12, regularClassCount, "12 classes ordinaires attendues"),
                () -> assertEquals(2, interfaceCount, "2 interfaces attendues"),
                () -> assertEquals(1, enumCount, "1 énumération attendue"),
                () -> assertEquals(59, totalMethods, "59 méthodes au total attendues"),
                () -> assertEquals(9, totalConstructors, "9 constructeurs attendus"),
                () -> assertEquals(21, totalAttributes, "21 attributs au total attendus")
        );
    }

    @Test
    @DisplayName("Détail LoanService et classe membre LoanService.Loan")
    void testLoanServiceAndNestedClass() {
        ClassInfo loanService = findClassBySuffix("LoanService");
        assertNotNull(loanService, "LoanService doit être présente");
        assertEquals(6, loanService.methods.size(), "LoanService doit déclarer 6 méthodes");
        assertEquals(5, loanService.fields.size(), "LoanService doit déclarer 5 attributs");

        ClassInfo loanMember = findClassBySuffix("LoanService.Loan");
        if (loanMember == null) {
            loanMember = findClassBySuffix("LoanService$Loan");
        }
        assertNotNull(loanMember, "La classe membre LoanService.Loan doit être présente");
        assertEquals(4, loanMember.methods.size(), "LoanService.Loan doit déclarer 4 méthodes");
        assertEquals(3, loanMember.fields.size(), "LoanService.Loan doit déclarer 3 attributs");
    }

    @Test
    @DisplayName("Superclasses de LoanException : Exception et Throwable")
    void testLoanExceptionSuperclasses() {
        ClassInfo loanException = findClassBySuffix("LoanException");
        assertNotNull(loanException, "LoanException doit être présente");

        assertAll(
                () -> assertTrue(loanException.superClasses.contains("java.lang.Exception"),
                        "java.lang.Exception doit être présente dans les superclasses"),
                () -> assertTrue(loanException.superClasses.contains("java.lang.Throwable"),
                        "java.lang.Throwable doit être présente dans les superclasses")
        );
    }

    @Test
    @DisplayName("Visibilités des attributs de library.model.Item")
    void testItemAttributesVisibility() {
        ClassInfo item = findClassBySuffix("Item");
        assertNotNull(item, "La classe Item doit être présente");

        Map<String, String> visibilityByField = item.fields.stream()
                .collect(Collectors.toMap(f -> f.name, f -> f.visibility));

        assertAll(
                () -> assertEquals("public", visibilityByField.get("MAX_LOAN_DAYS"), "MAX_LOAN_DAYS doit être public"),
                () -> assertEquals("protected", visibilityByField.get("id"), "id doit être protected"),
                () -> assertEquals("protected", visibilityByField.get("title"), "title doit être protected"),
                () -> assertEquals("private", visibilityByField.get("borrower"), "borrower doit être private"),
                () -> assertEquals("package-private", visibilityByField.get("category"), "category doit être package-private")
        );
    }

    private static ClassInfo findClassBySuffix(String suffix) {
        return classByName.values().stream()
                .filter(c -> c.qualifiedName != null && (c.qualifiedName.endsWith("." + suffix) || c.qualifiedName.equals(suffix)))
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