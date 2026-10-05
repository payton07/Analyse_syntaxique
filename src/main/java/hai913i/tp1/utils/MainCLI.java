package hai913i.tp1.utils;

import hai913i.tp1.analysis.MetricsService;
import hai913i.tp1.model.facts.MethodFact;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.model.facts.TypeFact;
import hai913i.tp1.model.infos.CallSiteInfo;
import hai913i.tp1.model.infos.ClassInfo;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.visitors.CallExtractorVisitor;
import hai913i.tp1.visitors.MetricsVisitor;
import hai913i.tp1.visitors.StructureVisitor;
import hai913i.tp1.visitors.TreeVisitor;
import java.util.*;

public class MainCLI {

    private final List<ParsedFile> files;
    private final Scanner scanner = new Scanner(System.in);
    private ProjectFacts facts;

    public MainCLI(List<ParsedFile> files) {
        this.files = files;
    }

    public void start() {
        boolean running = true;
        while (running) {
            System.out.println("==================================================");
            System.out.println("1 - Afficher l'arbre AST (A1)");
            System.out.println("2 - Afficher les statistiques AST brutes (A2)");
            System.out.println("3 - Afficher la structure détaillée (Classes & membres) (A2)");
            System.out.println("4 - Analyser les appels de méthodes (A3)");
            System.out.println("5 - Valider le modèle de faits indépendant (B1)");
            System.out.println("6 - Calculer les 13 métriques du projet (B2)");
            System.out.println("Q - Quitter");
            System.out.println("==================================================");
            System.out.print("Choix : ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> showAst();
                case "2" -> showMetrics();
                case "3" -> showStructure();
                case "4" -> showCalls();
                case "5" -> showFacts();
                case "6" -> showB2Metrics();
                case "q", "Q" -> running = false;
                default -> System.out.println("Option non reconnue.");
            }
            System.out.println();
        }
    }

    private ProjectFacts getOrBuildFacts() {
        if (this.facts == null) {
            StructureVisitor structVisitor = new StructureVisitor();
            CallExtractorVisitor callVisitor = new CallExtractorVisitor();
            for (ParsedFile f : this.files) {
                f.unit().accept(structVisitor);
                f.unit().accept(callVisitor);
            }
            this.facts = FactBuilder.build(this.files, structVisitor, callVisitor);
        }
        return this.facts;
    }

    private void showB2Metrics() {
        MetricsService service = new MetricsService(getOrBuildFacts());

        System.out.println("\n========== MÉTRIQUES DE L'APPLICATION (B2) ==========");
        System.out.printf("1.  Nombre de classes (types)         : %d%n", service.getNbClasses());
        System.out.printf("2.  Nombre de lignes de code (app)    : %d%n", service.getTotalAppLines());
        System.out.printf("3.  Nombre total de méthodes          : %d%n", service.getTotalMethods());
        System.out.printf("4.  Nombre total de paquetages        : %d%n", service.getTotalPackages());
        System.out.printf(Locale.FRANCE, "5.  Moyenne méthodes par classe       : %.2f%n", service.getAvgMethodsPerClass());
        System.out.printf(Locale.FRANCE, "6.  Moyenne lignes par méthode (corps): %.2f (%d lignes pour %d méthodes)%n",
                service.getAvgLinesPerMethodWithBody(),
                service.getTotalLinesOfMethodsWithBody(),
                service.getNbMethodsWithBody());
        System.out.printf(Locale.FRANCE, "7.  Moyenne attributs par  classe     : %.2f%n", service.getAvgAttributesPerClass());

        // Q8
        int nClasses = service.getNbClasses();
        int kClasses = (nClasses + 9) / 10;
        List<TypeFact> topMethods = service.getTop10PercentClassesByMethods();
        System.out.printf("8.  Top 10 %% classes avec le plus de méthodes (n = %d, k = %d -> %d retenues) :%n",
                nClasses, kClasses, topMethods.size());
        topMethods.forEach(t -> System.out.printf("     - %s (%d méthodes)%n", t.qualifiedName(), t.getMethodCount()));

        // Q9
        List<TypeFact> topAttrs = service.getTop10PercentClassesByAttributes();
        System.out.printf("9.  Top 10 %% classes avec le plus d'attributs (n = %d, k = %d -> %d retenues) :%n",
                nClasses, kClasses, topAttrs.size());
        topAttrs.forEach(t -> System.out.printf("     - %s (%d attributs)%n", t.qualifiedName(), t.getFieldCount()));

        // Q10
        List<TypeFact> inter = service.getIntersectionTopClasses();
        System.out.println("10. Classes dans les deux catégories précédentes :");
        if (inter.isEmpty()) {
            System.out.println("     (aucune)");
        } else {
            inter.forEach(t -> System.out.printf("     - %s%n", t.qualifiedName()));
        }

        // Q11 : Saisie utilisateur
        int x = askPositiveInteger("11. Entrez le seuil X (classes ayant strictement plus de X méthodes) : ");
        List<TypeFact> moreThanX = service.getClassesWithMoreThanXMethods(x);
        System.out.printf("    Classes possédant plus de %d méthodes (%d trouvées) :%n", x, moreThanX.size());
        moreThanX.forEach(t -> System.out.printf("     - %s (%d méthodes)%n", t.qualifiedName(), t.getMethodCount()));

        // Q12 : Affichage sous forme de tableau
        System.out.println("12. Top 10 % méthodes les plus longues par classe :");
        renderQ12Table(service);

        // Q13
        int maxParams = service.getMaxParameters();
        List<MethodFact> maxParamMethods = service.getMethodsWithMaxParameters();
        System.out.printf("13. Nombre maximal de paramètres : %d%n", maxParams);
        maxParamMethods.forEach(m -> System.out.printf("     - %s%n", m.methodKey()));
        System.out.println("======================================================");
    }

    private void renderQ12Table(MetricsService service) {
        Map<String, List<MethodFact>> topMethodsByClass = service.getTop10PercentMethodsByClass();
        ProjectFacts f = getOrBuildFacts();

        final String sep = "+------------------------------------------+-----+-----+------------------------------+-----+";
        System.out.println(sep);
        System.out.printf("| %-40s | %-3s | %-3s | %-28s | %-3s |%n", "Classe (Type)", "n", "k", "Méthode(s) retenue(s)", "LOC");
        System.out.println(sep);

        for (Map.Entry<String, TypeFact> entry : f.getTypes().entrySet()) {
            String className = entry.getKey();
            TypeFact type = entry.getValue();

            // Candidats : méthodes ayant un corps et des lignes > 0
            List<MethodFact> candidates = type.methodKeys().stream()
                    .map(k -> f.getMethods().get(k))
                    .filter(Objects::nonNull)
                    .filter(MethodFact::hasBody)
                    .filter(m -> m.linesOfCode() > 0)
                    .toList();

            int n = candidates.size();
            int k = (n + 9) / 10;
            List<MethodFact> tops = topMethodsByClass.getOrDefault(className, List.of());

            String displayClass = className.length() > 40 ? className.substring(0, 37) + "..." : className;

            if (tops.isEmpty()) {
                System.out.printf("| %-40s | %3d | %3d | %-28s | %-3s |%n",
                        displayClass, n, k, "(aucune)", "-");
            } else {
                for (int i = 0; i < tops.size(); i++) {
                    MethodFact m = tops.get(i);
                    String mName = m.simpleName().length() > 28 ? m.simpleName().substring(0, 25) + "..." : m.simpleName();
                    if (i == 0) {
                        System.out.printf("| %-40s | %3d | %3d | %-28s | %3d |%n",
                                displayClass, n, k, mName, m.linesOfCode());
                    } else {
                        System.out.printf("| %-40s | %-3s | %-3s | %-28s | %3d |%n",
                                "", "", "", mName, m.linesOfCode());
                    }
                }
            }
        }
        System.out.println(sep);
    }

    private int askPositiveInteger(String prompt) {
        while (true) {
            System.out.print(prompt);
            String input = scanner.nextLine().trim();
            try {
                int val = Integer.parseInt(input);
                if (val >= 0) return val;
                System.out.println("Veuillez saisir un entier positif ou nul.");
            } catch (NumberFormatException e) {
                System.out.println("Entrée invalide : veuillez entrer un entier.");
            }
        }
    }

    private void showAst() {
        System.out.print("Nom du fichier (ex: LoanService.java) ou [Entrée] pour tous : ");
        String filter = scanner.nextLine().trim();

        List<ParsedFile> targetFiles = filterFiles(filter);
        for (ParsedFile f : targetFiles) {
            System.out.println("\n--- AST de " + f.path().getFileName() + " ---");
            TreeVisitor visitor = new TreeVisitor();
            f.unit().accept(visitor);
        }
    }

    private void showMetrics() {
        System.out.print("Nom du fichier (ex: LoanService.java) ou [Entrée] pour le projet entier : ");
        String filter = scanner.nextLine().trim();

        List<ParsedFile> targetFiles = filterFiles(filter);
        MetricsVisitor metricsVisitor = new MetricsVisitor();

        for (ParsedFile f : targetFiles) {
            f.unit().accept(metricsVisitor);
        }

        System.out.println("\n" + metricsVisitor.getStats());
    }

    private void showStructure() {
        System.out.print("Nom du fichier (ex: LoanService.java) ou [Entrée] pour tout le projet : ");
        String filter = scanner.nextLine().trim();

        List<ParsedFile> targetFiles = filterFiles(filter);
        StructureVisitor structureVisitor = new StructureVisitor();

        for (ParsedFile f : targetFiles) {
            f.unit().accept(structureVisitor);
        }

        for (ClassInfo info : structureVisitor.getClasses()) {
            System.out.println(info);
        }
    }

    private void showCalls() {
        System.out.print("Nom du fichier (ex: LoanService.java) ou [Entrée] pour tout le projet : ");
        String filter = scanner.nextLine().trim();

        List<ParsedFile> targetFiles = filterFiles(filter);
        CallExtractorVisitor visitor = new CallExtractorVisitor();

        for (ParsedFile f : targetFiles) {
            f.unit().accept(visitor);
        }

        var callsByMethod = visitor.getCallsByMethod();
        Set<String> projectMethods = callsByMethod.keySet();

        int totalCalls = 0;
        int internalCalls = 0;
        int externalCalls = 0;
        int unresolvedCalls = 0;

        for (var entry : callsByMethod.entrySet()) {
            var callSites = entry.getValue();
            if (callSites.isEmpty()) continue;

            System.out.println("\nDans " + entry.getKey() + " :");
            for (CallSiteInfo site : callSites) {
                totalCalls++;
                System.out.printf("  - Ligne %d : %s | Receveur : %s | Cible : %s%n",
                        site.lineNumber, site.methodName, site.receiverStaticType, site.resolvedTarget);

                if ("non résolue".equals(site.resolvedTarget)) {
                    unresolvedCalls++;
                } else if (projectMethods.contains(site.resolvedTarget)) {
                    internalCalls++;
                } else {
                    externalCalls++;
                }
            }
        }

        System.out.println("\n--- Résumé A3 ---");
        System.out.println("Total appels     : " + totalCalls);
        System.out.println("Vers le projet   : " + internalCalls);
        System.out.println("Vers l'extérieur : " + externalCalls);
        System.out.println("Non résolus      : " + unresolvedCalls);
    }

    private void showFacts() {
        ProjectFacts f = getOrBuildFacts();

        long interfaces = f.getTypes().values().stream().filter(TypeFact::isInterface).count();
        long enums = f.getTypes().values().stream().filter(TypeFact::isEnum).count();
        long classes = f.getTypes().size() - interfaces - enums;
        int totalMethods = f.getMethods().size();
        long constructors = f.getMethods().values().stream().filter(MethodFact::isConstructor).count();
        int totalFields = f.getTypes().values().stream().mapToInt(TypeFact::getFieldCount).sum();

        var allCalls = f.getAllCalls();
        long unresolved = allCalls.stream().filter(c -> !c.isResolved()).count();
        long internal = allCalls.stream().filter(c -> f.isInternalMethod(c.resolvedTarget())).count();
        long external = allCalls.size() - internal - unresolved;

        System.out.println("\n================ POINT DE CONTRÔLE B1 ================");
        System.out.println("Métriques structurelles (A2 depuis faits) :");
        System.out.printf("  - Types        : %d (Classes: %d, Interfaces: %d, Enums: %d)%n",
                f.getTypes().size(), classes, interfaces, enums);
        System.out.printf("  - Méthodes     : %d (dont %d constructeurs)%n", totalMethods, constructors);
        System.out.printf("  - Attributs    : %d%n", totalFields);

        System.out.println("\nMétriques d'appels (A3 depuis faits) :");
        System.out.printf("  - Total appels : %d%n", allCalls.size());
        System.out.printf("  - Internes     : %d%n", internal);
        System.out.printf("  - Externes     : %d%n", external);
        System.out.printf("  - Non résolus  : %d%n", unresolved);
        System.out.println("======================================================");
    }

    private List<ParsedFile> filterFiles(String fileName) {
        if (fileName.isEmpty()) return this.files;
        return this.files.stream()
                .filter(f -> f.path().getFileName().toString().equalsIgnoreCase(fileName))
                .toList();
    }
}