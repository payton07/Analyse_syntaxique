package hai913i.tp1.utils;

import hai913i.tp1.model.facts.CallFact;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.model.facts.TypeFact;
import hai913i.tp1.model.infos.CallSiteInfo;
import hai913i.tp1.model.infos.ClassInfo;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.visitors.CallExtractorVisitor;
import hai913i.tp1.visitors.MetricsVisitor;
import hai913i.tp1.visitors.StructureVisitor;
import hai913i.tp1.visitors.TreeVisitor;

import java.util.List;
import java.util.Map;
import java.util.Scanner;
import java.util.Set;

public class MainCLI {

    private final List<ParsedFile> files;
    private final Scanner scanner = new Scanner(System.in);

    public MainCLI(List<ParsedFile> files) {
        this.files = files;
    }

    public void start() {
        boolean running = true;
        while (running) {
            System.out.println("==============================================================");
            System.out.println("1 - Afficher l'arbre AST (A1)");
            System.out.println("2 - Afficher les statistiques / métriques (A2)");
            System.out.println("3 - Afficher la structure détaillée (Classes & membres) (A2)");
            System.out.println("4 - Analyser les appels de méthodes (A3)");
            System.out.println("5 - Valider le modèle de faits indépendant (B1)");
            System.out.println("Q - Quitter");
            System.out.println("==============================================================");
            System.out.print("Choix : ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> showAst();
                case "2" -> showMetrics();
                case "3" -> showStructure();
                case "4" -> showCalls();
                case "5" -> showFacts();
                case "q", "Q" -> running = false;
                default -> System.out.println("Option non reconnue.");
            }
            System.out.println();
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

        Map<String, List<CallSiteInfo>> callsByMethod = visitor.getCallsByMethod();
        Set<String> projectMethods = callsByMethod.keySet();

        int totalCalls = 0;
        int internalCalls = 0;
        int externalCalls = 0;
        int unresolvedCalls = 0;

        for (Map.Entry<String, List<CallSiteInfo>> entry : callsByMethod.entrySet()) {
            List<CallSiteInfo> callSites = entry.getValue();
            if (callSites.isEmpty()) {
                continue;
            }

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
        System.out.println("Vers des méthodes du projet   : " + internalCalls);
        System.out.println("Vers l'extérieur : " + externalCalls);
        System.out.println("Non résolus      : " + unresolvedCalls);
    }

    private void showFacts() {
        System.out.println("\nConstruction de la base de faits (FactBuilder)...");
        StructureVisitor structVisitor = new StructureVisitor();
        CallExtractorVisitor callVisitor = new CallExtractorVisitor();

        for (ParsedFile f : this.files) {
            f.unit().accept(structVisitor);
            f.unit().accept(callVisitor);
        }

        ProjectFacts facts = FactBuilder.build(structVisitor, callVisitor);

        // Recalcul des métriques A2
        long interfaces = facts.getTypes().values().stream().filter(TypeFact::isInterface).count();
        long enums = facts.getTypes().values().stream().filter(TypeFact::isEnum).count();
        long classes = facts.getTypes().size() - interfaces - enums;
        int totalMethods = facts.getMethods().size();
        long constructors = facts.getMethods().values().stream().filter(m -> m.isConstructor()).count();
        int totalFields = facts.getTypes().values().stream().mapToInt(TypeFact::getFieldCount).sum();

        // Recalcul des métriques A3
        List<CallFact> allCalls = facts.getAllCalls();
        long unresolved = allCalls.stream().filter(c -> !c.isResolved()).count();
        long internal = allCalls.stream().filter(c -> facts.isInternalMethod(c.resolvedTarget())).count();
        long external = allCalls.size() - internal - unresolved;

        System.out.println("\n================ POINT DE CONTRÔLE B1 ========================");
        System.out.println("Métriques structurelles (A2 depuis faits) :");
        System.out.printf("  - Types        : %d (Classes: %d, Interfaces: %d, Enums: %d)%n",
                facts.getTypes().size(), classes, interfaces, enums);
        System.out.printf("  - Méthodes     : %d (dont %d constructeurs)%n", totalMethods, constructors);
        System.out.printf("  - Attributs    : %d%n", totalFields);

        System.out.println("\nMétriques d'appels (A3 depuis faits) :");
        System.out.printf("  - Total appels : %d%n", allCalls.size());
        System.out.printf("  - Internes     : %d%n", internal);
        System.out.printf("  - Externes     : %d%n", external);
        System.out.printf("  - Non résolus  : %d%n", unresolved);
        System.out.println("==============================================================");
    }

    private List<ParsedFile> filterFiles(String fileName) {
        if (fileName.isEmpty()) {
            return this.files;
        }
        return this.files.stream()
                .filter(f -> f.path().getFileName().toString().equalsIgnoreCase(fileName))
                .toList();
    }
}