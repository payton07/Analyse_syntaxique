package hai913i.tp1;

import static org.junit.jupiter.api.Assertions.assertAll;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;

import org.eclipse.jdt.core.dom.ASTVisitor;
import org.eclipse.jdt.core.dom.EnumDeclaration;
import org.eclipse.jdt.core.dom.FieldDeclaration;
import org.eclipse.jdt.core.dom.MethodDeclaration;
import org.eclipse.jdt.core.dom.SuperConstructorInvocation;
import org.eclipse.jdt.core.dom.TypeDeclaration;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;

/**
 * Point de contrôle A1 :
 * - Dvd.java : 1 TypeDeclaration, 1 FieldDeclaration, 3 MethodDeclaration
 *   (dont le constructeur avec SuperConstructorInvocation).
 * - Category.java : le type racine est un EnumDeclaration, ignoré par visit(TypeDeclaration).
 */
class ControlPointA1Test {

    private static List<ParsedFile> files;

    @BeforeAll
    static void setUp() throws Exception {
        Path mainRoot = resolveResourcePath("validation");
        ProjectSources sources = ProjectSources.of(mainRoot);
        files = JdtParser.parse(sources, List.of());
    }

    @Test
    @DisplayName("Dvd.java : structure AST (1 type, 1 champ, 3 méthodes, 1 super-constructeur)")
    void testDvdAstStructure() {
        ParsedFile dvdFile = findFile("Dvd.java");
        assertNotNull(dvdFile, "Dvd.java doit être présent dans les sources analysées");

        List<TypeDeclaration> typeDecls = new ArrayList<>();
        List<FieldDeclaration> fieldDecls = new ArrayList<>();
        List<MethodDeclaration> methodDecls = new ArrayList<>();
        List<SuperConstructorInvocation> superConstructorInvocations = new ArrayList<>();

        dvdFile.unit().accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration node) {
                typeDecls.add(node);
                return super.visit(node);
            }

            @Override
            public boolean visit(FieldDeclaration node) {
                fieldDecls.add(node);
                return super.visit(node);
            }

            @Override
            public boolean visit(MethodDeclaration node) {
                methodDecls.add(node);
                return super.visit(node);
            }

            @Override
            public boolean visit(SuperConstructorInvocation node) {
                superConstructorInvocations.add(node);
                return super.visit(node);
            }
        });

        long constructorCount = methodDecls.stream()
                .filter(MethodDeclaration::isConstructor)
                .count();

        assertAll(
                () -> assertEquals(1, typeDecls.size(), "Dvd.java doit contenir 1 TypeDeclaration"),
                () -> assertEquals(1, fieldDecls.size(), "Dvd.java doit contenir 1 FieldDeclaration"),
                () -> assertEquals(3, methodDecls.size(), "Dvd.java doit contenir 3 MethodDeclaration"),
                () -> assertEquals(1, constructorCount, "Dvd.java doit contenir exactement 1 constructeur"),
                () -> assertEquals(1, superConstructorInvocations.size(),
                        "Le constructeur de Dvd doit contenir un SuperConstructorInvocation (super(...))")
        );
    }

    @Test
    @DisplayName("Category.java : racine est un EnumDeclaration non capturé par visit(TypeDeclaration)")
    void testCategoryIsEnumDeclaration() {
        ParsedFile categoryFile = findFile("Category.java");
        assertNotNull(categoryFile, "Category.java doit être présent dans les sources analysées");

        List<TypeDeclaration> typeDecls = new ArrayList<>();
        List<EnumDeclaration> enumDecls = new ArrayList<>();

        categoryFile.unit().accept(new ASTVisitor() {
            @Override
            public boolean visit(TypeDeclaration node) {
                typeDecls.add(node);
                return super.visit(node);
            }

            @Override
            public boolean visit(EnumDeclaration node) {
                enumDecls.add(node);
                return super.visit(node);
            }
        });

        assertAll(
                () -> assertEquals(1, enumDecls.size(),
                        "La racine du type de Category.java doit être un EnumDeclaration"),
                () -> assertEquals("Category", enumDecls.get(0).getName().getIdentifier(),
                        "L'énumération doit s'appeler Category"),
                () -> assertTrue(typeDecls.isEmpty(),
                        "visit(TypeDeclaration) ne doit voir aucun nœud sur Category.java")
        );
    }

    // --- Utilitaires de recherche ---

    private static ParsedFile findFile(String fileName) {
        return files.stream()
                .filter(f -> f.path().getFileName().toString().equals(fileName))
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