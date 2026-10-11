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
import hai913i.tp1.analysis.GraphView;
import hai913i.tp1.analysis.MetricsService;
import hai913i.tp1.cli.Options;
import hai913i.tp1.model.facts.CallFact;
import hai913i.tp1.model.facts.MethodFact;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.model.facts.TypeFact;
import hai913i.tp1.model.infos.ClassInfo;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.spoon.EngineComparison;
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
        if (Options.SPOON.equals(analysis.engine())) {
            out.println("Moteur d'extraction    : Spoon (les faits ne viennent pas de JDT)");
        }
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
        if (Options.SPOON.equals(analysis.engine())) {
            structureFromFacts();
            return;
        }
        out.println("\n========== STRUCTURE DU PROJET (A2) ==========");
        for (ClassInfo info : analysis.classes()) {
            out.println(info);
        }
        out.println(analysis.stats());
    }

    // Avec Spoon, la structure est relue dans le modèle de faits : ClassInfo est rempli par JDT
    private void structureFromFacts() {
        ProjectFacts facts = analysis.facts();
        out.println("\n========== STRUCTURE DU PROJET (A2, extraite avec Spoon) ==========");
        for (TypeFact type : facts.getTypes().values()) {
            out.println("==================================================");
            out.printf("%s : %s (package: %s)%n", type.kind(), type.qualifiedName(), type.packageName());
            out.println("Superclasses : " + (type.superClasses().isEmpty() ? "aucune" : String.join(", ", type.superClasses())));
            out.println("Interfaces directes : " + (type.interfaces().isEmpty() ? "aucune" : String.join(", ", type.interfaces())));
            out.printf("Attributs (%d) :%n", type.getFieldCount());
            type.fields().forEach(f -> out.printf("  - Attribut: %s %s [visibilité: %s]%n",
                    f.declaredType(), f.name(), f.visibility()));
            out.printf("Méthodes (%d) :%n", type.getMethodCount());
            for (String key : type.methodKeys()) {
                MethodFact method = facts.getMethods().get(key);
                out.printf("  - %s: %s (paramètres: %d)%n", method.isConstructor() ? "Constructeur" : "Méthode",
                        method.isConstructor() ? simpleName(type.qualifiedName()) : method.simpleName(),
                        method.parameterCount());
            }
            out.println();
        }
    }

    private static String simpleName(String qualifiedName) {
        return qualifiedName.substring(qualifiedName.lastIndexOf('.') + 1);
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
        graph(constructorArcs, methodFragment, dotFile, null, GraphView.Filter.NONE);
    }

    /**
     * @param imageFile image à produire avec Graphviz, ou null
     * @param filter    ce qui est dessiné dans le fichier DOT et dans l'image (C2)
     */
    public void graph(boolean constructorArcs, String methodFragment, Path dotFile,
                      Path imageFile, GraphView.Filter filter) throws IOException {
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
        if (dotFile != null || imageFile != null) {
            draw(graph, dotFile, imageFile, filter);
        }
    }

    // C2 : sans filtre, le fichier DOT est celui de B3 ; sinon on dessine une vue du graphe
    private void draw(CallGraph graph, Path dotFile, Path imageFile, GraphView.Filter filter) throws IOException {
        String dot;
        if (filter.isNone()) {
            dot = DotExporter.export(graph);
        } else {
            GraphView view = GraphView.of(graph, analysis.facts(), filter);
            dot = DotExporter.export(view, analysis.facts());
            out.printf("%nVue dessinée : %d nœuds sur %d, %d arcs sur %d%n", view.nodes().size(),
                    graph.getNodes().size(), view.edges().size(), graph.getEdges().size());
        }
        if (dotFile != null) {
            Files.writeString(dotFile, dot);
            out.println("\nGraphe écrit dans " + dotFile.toAbsolutePath());
        }
        if (imageFile != null) {
            GraphvizRenderer.render(dot, imageFile);
            out.println("\nImage écrite dans " + imageFile.toAbsolutePath());
        }
    }

    // ------------------------------------------------------------------------
    // C1 : Spoon brut, Spoon avec les conventions du sujet, JDT
    // ------------------------------------------------------------------------
    public void compare() throws IOException {
        Analysis jdtAnalysis = Options.JDT.equals(analysis.engine())
                ? analysis : Analysis.of(analysis.sources().projectDirectory());
        ProjectFacts jdt = jdtAnalysis.facts();
        EngineComparison comparison = EngineComparison.of(analysis.sources(), jdt);
        ProjectFacts spoon = comparison.spoonFacts();
        EngineComparison.RawCounts raw = comparison.raw();

        out.println("\n========== COMPARAISON JDT / SPOON (C1) ==========");
        final String format = "%-26s | %-30s | %-19s | %s%n";
        out.printf(format, "Mesure", "Spoon brut", "Spoon + conventions", "JDT");
        out.println("-".repeat(92));
        out.printf(format, "Types",
                raw.topLevelTypes() + " (premier niveau), " + raw.allTypes(),
                spoon.getTypes().size(), jdt.getTypes().size());
        out.printf(format, "Méthodes",
                raw.methods() + " + " + raw.constructors() + " constr. (" + raw.implicitConstructors() + " impl.)",
                spoon.getMethods().size(), jdt.getMethods().size());
        out.printf(format, "Attributs",
                raw.fields() + " (" + raw.enumValues() + " constantes d'enum)",
                fieldCount(spoon), fieldCount(jdt));
        out.printf(format, "Paquetages", raw.packages(), spoon.getPackages().size(), jdt.getPackages().size());
        out.printf(format, "Appels",
                raw.invocations() + " (" + raw.constructorInvocations() + " constr., "
                        + raw.implicitInvocations() + " impl.)",
                spoon.getAllCalls().size(), jdt.getAllCalls().size());
        out.printf(format, "Graphe (nœuds/arcs/sites)", "-",
                graphSummary(new CallGraph(spoon, false)), graphSummary(new CallGraph(jdt, false)));

        out.println("\nCibles proposées par Spoon pour un type non vérifié (mode noClasspath) : "
                + comparison.guessedTargets().size());
        comparison.guessedTargets().forEach(g -> out.println("  " + g));

        out.println("\nÉcarts entre les deux modèles de faits : " + comparison.differences().size());
        comparison.differences().forEach(d -> out.println("  " + d));
        if (comparison.differences().isEmpty()) {
            out.println("  (aucun : mêmes types, attributs, méthodes et appels)");
        }
    }

    private static int fieldCount(ProjectFacts facts) {
        return facts.getTypes().values().stream().mapToInt(TypeFact::getFieldCount).sum();
    }

    private static String graphSummary(CallGraph graph) {
        return graph.getNodes().size() + " / " + graph.getEdges().size() + " / " + graph.getInternalCallSites();
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
