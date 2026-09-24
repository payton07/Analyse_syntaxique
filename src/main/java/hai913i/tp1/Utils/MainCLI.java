package hai913i.tp1.Utils;

import hai913i.tp1.model.ClassInfo;
import hai913i.tp1.Visitors.MetricsVisitor;
import hai913i.tp1.Visitors.StructureVisitor;
import hai913i.tp1.Visitors.TreeVisitor;
import hai913i.tp1.parse.JdtParser.ParsedFile;

import java.util.List;
import java.util.Scanner;

public class MainCLI {

    private final List<ParsedFile> files;
    private final Scanner scanner = new Scanner(System.in);

    public MainCLI(List<ParsedFile> files) {
        this.files = files; // Aucun parcours ici, démarrage instantané
    }

    public void start() {
        boolean running = true;
        while (running) {
            System.out.println("==================================================");
            System.out.println("1 - Afficher l'arbre AST");
            System.out.println("2 - Afficher les statistiques / métriques");
            System.out.println("3 - Afficher la structure détaillée (Classes & membres)");
            System.out.println("Q - Quitter");
            System.out.println("==================================================");
            System.out.print("Choix : ");
            String choice = scanner.nextLine().trim();

            switch (choice) {
                case "1" -> showAst();
                case "2" -> showMetrics();
                case "3" -> showStructure();
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

    private List<ParsedFile> filterFiles(String fileName) {
        if (fileName.isEmpty()) {
            return this.files;
        }
        return this.files.stream()
                .filter(f -> f.path().getFileName().toString().equalsIgnoreCase(fileName))
                .toList();
    }
}