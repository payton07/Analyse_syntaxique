package hai913i.tp1.cli;

import java.io.IOException;
import java.io.PrintStream;
import java.nio.file.Path;
import java.util.Scanner;

import hai913i.tp1.report.ConsoleReport;

/**
 * Menu interactif (action « menu ») : une seconde façon de lancer les mêmes affichages
 * que la ligne de commande. Il ne contient aucun calcul.
 */
public class InteractiveMenu {

    private final ConsoleReport report;
    private final PrintStream out;
    private final Scanner scanner = new Scanner(System.in);

    public InteractiveMenu(ConsoleReport report, PrintStream out) {
        this.report = report;
        this.out = out;
    }

    public void start() throws IOException {
        while (true) {
            out.println("\n==================================================");
            out.println("1 - Afficher l'arbre AST (A1)");
            out.println("2 - Afficher la structure du projet (A2)");
            out.println("3 - Analyser les appels de méthodes (A3)");
            out.println("4 - Valider le modèle de faits indépendant (B1)");
            out.println("5 - Calculer les 13 métriques du projet (B2)");
            out.println("6 - Construire et interroger le graphe d'appel (B3)");
            out.println("Q - Quitter");
            out.println("==================================================");

            switch (ask("Choix : ")) {
                case "1" -> showAst();
                case "2" -> report.structure();
                case "3" -> report.calls();
                case "4" -> report.facts();
                case "5" -> report.metrics(askX());
                case "6" -> showGraph();
                case "q", "Q" -> {
                    return;
                }
                default -> out.println("Option non reconnue.");
            }
        }
    }

    private void showAst() {
        String file = ask("Nom du fichier (ex: Dvd.java) ou [Entrée] pour tous : ");
        try {
            report.ast(file.isEmpty() ? null : file);
        } catch (IllegalArgumentException e) {
            out.println(e.getMessage());
        }
    }

    private void showGraph() throws IOException {
        boolean constructorArcs = ask("Ajouter les arcs vers les constructeurs ? [o/N] : ").equalsIgnoreCase("o");
        String method = ask("Méthode à interroger (partie de sa clé, ex: Catalog#add) ou [Entrée] pour passer : ");
        String dot = ask("Fichier DOT à écrire (ex: callgraph.dot) ou [Entrée] pour ne pas exporter : ");

        report.graph(constructorArcs, method.isEmpty() ? null : method, dot.isEmpty() ? null : Path.of(dot));
    }

    // En mode interactif, une valeur mal formée est refusée puis redemandée
    private int askX() {
        while (true) {
            try {
                return Options.parseX(ask("Seuil X (classes ayant strictement plus de X méthodes) : "));
            } catch (IllegalArgumentException e) {
                out.println(e.getMessage());
            }
        }
    }

    private String ask(String prompt) {
        out.print(prompt);
        // Entrée fermée (fin de fichier) : on quitte proprement au lieu de lever une exception
        return scanner.hasNextLine() ? scanner.nextLine().trim() : "q";
    }
}
