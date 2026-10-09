package hai913i.tp1;

import java.io.IOException;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;

import org.eclipse.jdt.core.compiler.IProblem;

import hai913i.tp1.analysis.ProjectStats;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.model.infos.ClassInfo;
import hai913i.tp1.parse.JdtParser;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.parse.ProjectSources;
import hai913i.tp1.utils.FactBuilder;
import hai913i.tp1.visitors.CallExtractorVisitor;
import hai913i.tp1.visitors.MetricsVisitor;
import hai913i.tp1.visitors.StructureVisitor;

/**
 * Résultat d'une analyse complète : la chaîne du CM 02 exécutée une seule fois.
 *
 * fichiers .java -> AST + bindings (JdtParser) -> visiteurs -> modèle de faits (FactBuilder).
 * Tout ce qui suit (métriques, graphe, affichage) lit ce résultat sans relancer l'analyse.
 */
public record Analysis(
        ProjectSources sources,
        List<ParsedFile> files,
        List<String> compileErrors,   // « Fichier.java:ligne message », une entrée par erreur
        List<ClassInfo> classes,      // structure détaillée (A2), triée par nom qualifié
        ProjectStats stats,           // comptages bruts (A2)
        ProjectFacts facts            // modèle de faits indépendant de JDT (B1)
) {

    /**
     * @throws IllegalArgumentException si le dossier n'existe pas ou ne contient aucun fichier .java
     */
    public static Analysis of(Path project) throws IOException {
        ProjectSources sources = ProjectSources.of(project);

        // Ordre des fichiers fixé : la sortie ne dépend pas de l'ordre de rappel du parseur
        List<ParsedFile> files = new ArrayList<>(JdtParser.parse(sources, List.of()));
        files.sort(Comparator.comparing(ParsedFile::path));

        // Un fichier qui ne compile pas est signalé, mais son AST partiel est quand même analysé
        List<String> compileErrors = new ArrayList<>();
        for (ParsedFile file : files) {
            for (IProblem problem : file.unit().getProblems()) {
                if (problem.isError()) {
                    compileErrors.add(file.path().getFileName() + ":" + problem.getSourceLineNumber()
                            + " " + problem.getMessage());
                }
            }
        }

        StructureVisitor structVisitor = new StructureVisitor();
        CallExtractorVisitor callVisitor = new CallExtractorVisitor();
        MetricsVisitor metricsVisitor = new MetricsVisitor();
        for (ParsedFile file : files) {
            file.unit().accept(structVisitor);
            file.unit().accept(callVisitor);
            file.unit().accept(metricsVisitor);
        }

        List<ClassInfo> classes = new ArrayList<>(structVisitor.getClasses());
        classes.sort(Comparator.comparing(c -> c.qualifiedName));

        ProjectFacts facts = FactBuilder.build(files, structVisitor, callVisitor);
        return new Analysis(sources, files, compileErrors, classes, metricsVisitor.getStats(), facts);
    }
}
