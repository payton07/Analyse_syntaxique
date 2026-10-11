package hai913i.tp1;

import java.io.IOException;
import java.io.PrintStream;

import hai913i.tp1.cli.InteractiveMenu;
import hai913i.tp1.cli.Options;
import hai913i.tp1.report.ConsoleReport;

/**
 * Point d'entrée en ligne de commande : lit les arguments, lance l'analyse et délègue l'affichage.
 *
 * Usage : java -jar target/hai913i-tp1-analyzer.jar PROJET ACTION [options]   (voir Options.USAGE)
 *
 * Codes de sortie : 0 succès, 2 arguments invalides, 3 analyse impossible, 1 erreur inattendue.
 */
public final class Main {

    public static final int EXIT_OK = 0;
    public static final int EXIT_UNEXPECTED = 1;
    public static final int EXIT_USAGE = 2;
    public static final int EXIT_ANALYSIS = 3;

    private Main() {
    }

    public static void main(String[] args) {
        System.exit(run(args, System.out, System.err));
    }

    /**
     * Toute la logique de main, sans System.exit : les tests peuvent l'appeler et lire le code de sortie.
     */
    static int run(String[] args, PrintStream out, PrintStream err) {
        if (args.length == 1 && (args[0].equals("-h") || args[0].equals("--help"))) {
            out.print(Options.USAGE);
            return EXIT_OK;
        }

        // 1. Arguments : validés avant de lancer une analyse qui serait inutile
        Options options;
        try {
            options = Options.parse(args);
        } catch (IllegalArgumentException e) {
            err.println("Erreur : " + e.getMessage());
            err.println();
            err.print(Options.USAGE);
            return EXIT_USAGE;
        }

        // 2. Analyse, puis 3. affichage de l'action demandée
        try {
            Analysis analysis = Analysis.of(options.project(), options.engine());
            ConsoleReport report = new ConsoleReport(analysis, out);

            report.header();
            switch (options.action()) {
                case "ast" -> report.ast(options.file());
                case "structure" -> report.structure();
                case "appels" -> report.calls();
                case "faits" -> report.facts();
                case "metriques" -> report.metrics(options.x());
                case "graphe" -> report.graph(options.constructorArcs(), options.method(), options.dot(),
                        options.image(), options.view());
                case "tout" -> {
                    report.structure();
                    report.calls();
                    report.metrics(options.x());
                    report.graph(options.constructorArcs(), options.method(), options.dot(),
                            options.image(), options.view());
                }
                case "menu" -> new InteractiveMenu(report, out).start();
                case "comparer" -> report.compare();
                default -> throw new IllegalStateException("Action non traitée : " + options.action());
            }
            return EXIT_OK;
        } catch (IllegalArgumentException e) {
            // Dossier inexistant, dossier sans fichier Java, fichier demandé introuvable
            err.println("Erreur : " + e.getMessage());
            return EXIT_ANALYSIS;
        } catch (IOException e) {
            err.println("Erreur de lecture ou d'écriture : " + e.getMessage());
            return EXIT_ANALYSIS;
        } catch (RuntimeException e) {
            // Dernier filet : jamais de pile d'exception brute
            err.println("Erreur inattendue (" + e.getClass().getSimpleName() + ") : " + e.getMessage());
            return EXIT_UNEXPECTED;
        }
    }
}
