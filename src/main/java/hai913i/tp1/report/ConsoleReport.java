package hai913i.tp1.report;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import hai913i.tp1.Analysis;
import hai913i.tp1.analysis.CallGraph;
import hai913i.tp1.analysis.DotExporter;
import hai913i.tp1.analysis.MetricsService;
import hai913i.tp1.model.facts.CallFact;
import hai913i.tp1.model.facts.MethodFact;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.model.facts.TypeFact;
import hai913i.tp1.model.infos.ClassInfo;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.visitors.TreeVisitor;

/**
 * Affichage texte des résultats d'une analyse. Une méthode par action de la ligne de commande.
 *
 * Cette classe ne calcule rien : elle met en forme ce que fournissent Analysis, MetricsService
 * et CallGraph. Le flux de sortie est reçu en paramètre, ce qui permet de la tester.
 */
public class ConsoleReport {

    private final Analysis analysis;
    private final PrintStream out;

    public ConsoleReport(Analysis analysis, PrintStream out) {
        this.analysis = analysis;
        this.out = out;
    }

    // ------------------------------------------------------------------------
    // A0 : en-tête, affiché avant chaque action
    // ------------------------------------------------------------------------
    public void header() {
        out.println("Racine des sources     : " + analysis.sources().sourceRoot());
        out.println("Unites de compilation  : " + analysis.files().size());
        out.println("Erreurs de compilation : " + analysis.compileErrors().size());
        for (String error : analysis.compileErrors()) {
            out.println("  " + error);
        }
        if (!analysis.compileErrors().isEmpty()) {
            out.println("Attention : l'analyse continue, mais les fichiers en erreur sont analysés partiellement.");
        }
    }

    // ------------------------------------------------------------------------
    // A1 : arbre syntaxique
    // ------------------------------------------------------------------------
    /**
     * @param fileName nom d'un fichier (Dvd.java), ou null pour tous les fichiers
     * @throws IllegalArgumentException si aucun fichier analysé ne porte ce nom
     */
    public void ast(String fileName) {
        List<ParsedFile> targets = analysis.files().stream()
                .filter(f -> fileName == null || f.path().getFileName().toString().equalsIgnoreCase(fileName))
                .toList();
        if (targets.isEmpty()) {
            throw new IllegalArgumentException("Aucun fichier analysé ne s'appelle « " + fileName + " ».");
        }
        for (ParsedFile file : targets) {
            out.println("\n--- AST de " + file.path().getFileName() + " ---");
            file.unit().accept(new TreeVisitor(out));
        }
    }

    // ------------------------------------------------------------------------
    // A2 : structure
    // ------------------------------------------------------------------------
    public void structure() {
        out.println("\n========== STRUCTURE DU PROJET (A2) ==========");
        for (ClassInfo info : analysis.classes()) {
            out.println(info);
        }
        out.println(analysis.stats());
    }

    // ------------------------------------------------------------------------
    // A3 : appels et receveurs
    // ------------------------------------------------------------------------
    public void calls() {
        ProjectFacts facts = analysis.facts();
        int total = 0, internal = 0, external = 0, unresolved = 0;

        out.println("\n========== APPELS ET RECEVEURS (A3) ==========");
        for (MethodFact method : facts.getMethods().values()) {
            if (method.calls().isEmpty()) continue;

            out.println("\nDans " + method.methodKey() + " :");
            for (CallFact call : method.calls()) {
                out.printf("  - Ligne %d : %s | Receveur : %s | Cible : %s%n",
                        call.lineNumber(), call.methodName(), call.receiverStaticType(), call.resolvedTarget());
                total++;
                if (!call.isResolved()) unresolved++;
                else if (facts.isInternalMethod(call.resolvedTarget())) internal++;
                else external++;
            }
        }

        out.println("\n--- Résumé A3 ---");
        out.println("Total appels     : " + total);
        out.println("Vers le projet   : " + internal);
        out.println("Vers l'extérieur : " + external);
        out.println("Non résolus      : " + unresolved);
    }

    // ------------------------------------------------------------------------
    // B1 : comptages relus depuis le modèle de faits
    // ------------------------------------------------------------------------
    public void facts() {
        ProjectFacts f = analysis.facts();

        long interfaces = f.getTypes().values().stream().filter(TypeFact::isInterface).count();
        long enums = f.getTypes().values().stream().filter(TypeFact::isEnum).count();
        long classes = f.getTypes().size() - interfaces - enums;
        long constructors = f.getMethods().values().stream().filter(MethodFact::isConstructor).count();
        int totalFields = f.getTypes().values().stream().mapToInt(TypeFact::getFieldCount).sum();

        List<CallFact> allCalls = f.getAllCalls();
        long unresolved = allCalls.stream().filter(c -> !c.isResolved()).count();
        long internal = allCalls.stream().filter(c -> f.isInternalMethod(c.resolvedTarget())).count();
        long external = allCalls.size() - internal - unresolved;

        out.println("\n========== MODÈLE DE FAITS (B1) ==========");
        out.printf("Types        : %d (classes et records : %d, interfaces : %d, énumérations : %d)%n",
                f.getTypes().size(), classes, interfaces, enums);
        out.printf("Méthodes     : %d (dont %d constructeurs)%n", f.getMethods().size(), constructors);
        out.printf("Attributs    : %d%n", totalFields);
        out.printf("Appels       : %d (internes : %d, externes : %d, non résolus : %d)%n",
                allCalls.size(), internal, external, unresolved);
    }

    // ------------------------------------------------------------------------
    // B2 : métriques
    // ------------------------------------------------------------------------
    public void metrics(int x) {
        MetricsService service = new MetricsService(analysis.facts());

        out.println("\n========== MÉTRIQUES DE L'APPLICATION (B2) ==========");
        out.printf("1.  Nombre de classes (types)         : %d%n", service.getNbClasses());
        out.printf("2.  Nombre de lignes de code (app)    : %d%n", service.getTotalAppLines());
        out.printf("3.  Nombre total de méthodes          : %d%n", service.getTotalMethods());
        out.printf("4.  Nombre total de paquetages        : %d%n", service.getTotalPackages());
        out.printf(Locale.FRANCE, "5.  Moyenne méthodes par classe       : %.2f%n", service.getAvgMethodsPerClass());
        out.printf(Locale.FRANCE, "6.  Moyenne lignes par méthode (corps): %.2f (%d lignes pour %d méthodes)%n",
                service.getAvgLinesPerMethodWithBody(),
                service.getTotalLinesOfMethodsWithBody(),
                service.getNbMethodsWithBody());
        out.printf(Locale.FRANCE, "7.  Moyenne attributs par classe      : %.2f%n", service.getAvgAttributesPerClass());

        int nClasses = service.getNbClasses();
        int kClasses = (nClasses + 9) / 10;

        List<TypeFact> topMethods = service.getTop10PercentClassesByMethods();
        out.printf("8.  Top 10 %% classes avec le plus de méthodes (n = %d, k = %d -> %d retenues) :%n",
                nClasses, kClasses, topMethods.size());
        topMethods.forEach(t -> out.printf("     - %s (%d méthodes)%n", t.qualifiedName(), t.getMethodCount()));

        List<TypeFact> topAttrs = service.getTop10PercentClassesByAttributes();
        out.printf("9.  Top 10 %% classes avec le plus d'attributs (n = %d, k = %d -> %d retenues) :%n",
                nClasses, kClasses, topAttrs.size());
        topAttrs.forEach(t -> out.printf("     - %s (%d attributs)%n", t.qualifiedName(), t.getFieldCount()));

        List<TypeFact> inter = service.getIntersectionTopClasses();
        out.println("10. Classes dans les deux catégories précédentes :");
        if (inter.isEmpty()) out.println("     (aucune)");
        inter.forEach(t -> out.printf("     - %s%n", t.qualifiedName()));

        List<TypeFact> moreThanX = service.getClassesWithMoreThanXMethods(x);
        out.printf("11. Classes possédant strictement plus de X = %d méthodes (%d trouvées) :%n", x, moreThanX.size());
        moreThanX.forEach(t -> out.printf("     - %s (%d méthodes)%n", t.qualifiedName(), t.getMethodCount()));

        out.println("12. Top 10 % méthodes les plus longues par classe :");
        q12Table(service);

        out.printf("13. Nombre maximal de paramètres : %d%n", service.getMaxParameters());
        service.getMethodsWithMaxParameters().forEach(m -> out.printf("     - %s%n", m.methodKey()));
    }

    private void q12Table(MetricsService service) {
        Map<String, List<MethodFact>> topMethodsByClass = service.getTop10PercentMethodsByClass();

        final String sep = "+------------------------------------------+-----+-----+------------------------------+-----+";
        out.println(sep);
        out.printf("| %-40s | %-3s | %-3s | %-28s | %-3s |%n", "Classe (Type)", "n", "k", "Méthode(s) retenue(s)", "LOC");
        out.println(sep);

        for (TypeFact type : analysis.facts().getTypes().values()) {
            // Candidats : toutes les méthodes de la classe (§ 4.3)
            int n = type.getMethodCount();
            int k = (n + 9) / 10;
            List<MethodFact> tops = topMethodsByClass.getOrDefault(type.qualifiedName(), List.of());
            String className = shorten(type.qualifiedName(), 40);

            if (tops.isEmpty()) {
                out.printf("| %-40s | %3d | %3d | %-28s | %-3s |%n", className, n, k, "(aucune)", "-");
            }
            for (int i = 0; i < tops.size(); i++) {
                MethodFact m = tops.get(i);
                if (i == 0) {
                    out.printf("| %-40s | %3d | %3d | %-28s | %3d |%n",
                            className, n, k, shorten(m.simpleName(), 28), m.linesOfCode());
                } else {
                    out.printf("| %-40s | %-3s | %-3s | %-28s | %3d |%n",
                            "", "", "", shorten(m.simpleName(), 28), m.linesOfCode());
                }
            }
        }
        out.println(sep);
    }

    private static String shorten(String text, int max) {
        return text.length() > max ? text.substring(0, max - 3) + "..." : text;
    }

    // ------------------------------------------------------------------------
    // B3 : graphe d'appel
    // ------------------------------------------------------------------------
    /**
     * @param methodFragment partie de la clé d'une méthode à interroger, ou null
     * @param dotFile        fichier DOT à écrire, ou null
     */
    public void graph(boolean constructorArcs, String methodFragment, Path dotFile) throws IOException {
        CallGraph graph = new CallGraph(analysis.facts(), constructorArcs);
        List<CallGraph.Edge> edges = graph.getEdges();

        out.println("\n========== GRAPHE D'APPEL (B3) ==========");
        out.printf("Option arcs vers les constructeurs : %s%n", constructorArcs ? "oui" : "non");
        out.printf("Nœuds (méthodes du projet) : %d%n", graph.getNodes().size());
        out.printf("Arcs                       : %d%n", edges.size());
        out.printf("Sites d'appel internes     : %d%n", graph.getInternalCallSites());
        out.printf("Appels externes            : %d%n", graph.getExternalCalls());
        out.printf("Appels non résolus         : %d%n", graph.getUnresolvedCalls());

        out.println("\nArcs (appelant -> appelée [poids]) :");
        for (CallGraph.Edge edge : edges) {
            out.printf("  %s -> %s [%d]%n", edge.source(), edge.target(), edge.weight());
        }

        if (methodFragment != null) {
            callersAndCallees(graph, methodFragment);
        }
        if (dotFile != null) {
            Files.writeString(dotFile, DotExporter.export(graph));
            out.println("\nGraphe écrit dans " + dotFile.toAbsolutePath());
        }
    }

    private void callersAndCallees(CallGraph graph, String fragment) {
        List<String> matches = graph.findNodes(fragment);
        if (matches.isEmpty()) {
            out.println("\nAucune méthode du projet ne contient « " + fragment + " ».");
            return;
        }
        for (String method : matches) {
            out.println("\n" + method);

            Map<String, Integer> callees = graph.getCallees(method);
            out.printf("  Appelées (%d) :%n", callees.size());
            if (callees.isEmpty()) out.println("    (aucune)");
            callees.forEach((callee, weight) -> out.printf("    -> %s [%d]%n", callee, weight));

            Map<String, Integer> callers = graph.getCallers(method);
            out.printf("  Appelantes (%d) :%n", callers.size());
            if (callers.isEmpty()) out.println("    (aucune)");
            callers.forEach((caller, weight) -> out.printf("    <- %s [%d]%n", caller, weight));
        }
    }
}
