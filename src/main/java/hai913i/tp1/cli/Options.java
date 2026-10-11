package hai913i.tp1.cli;

import java.nio.file.Path;
import java.util.List;

import hai913i.tp1.analysis.GraphView;

/**
 * Arguments de la ligne de commande, lus et validés avant toute analyse.
 *
 * Syntaxe : PROJET ACTION [options]
 */
public record Options(
        Path project,
        String action,
        Integer x,                 // seuil X (question 11) ; null s'il n'est pas fourni
        String file,               // --fichier : fichier dont on affiche l'AST
        boolean constructorArcs,   // --constructeurs : arcs vers les constructeurs dans le graphe
        String method,             // --methode : méthode dont on veut les appelées et les appelantes
        Path dot,                  // --dot : fichier DOT à écrire
        String engine,             // --moteur : jdt (par défaut) ou spoon
        Path image,                // --image : image du graphe à produire avec Graphviz
        GraphView.Filter view      // --paquetage, --voisinage, --sans-isoles, --par-classe
) {

    public static final String JDT = "jdt";
    public static final String SPOON = "spoon";

    public static final List<String> ACTIONS =
            List.of("ast", "structure", "appels", "faits", "metriques", "graphe", "tout", "menu", "comparer");

    // Chemin seul, sans action : seul l'en-tête est affiché (commande du point de contrôle A0)
    public static final String HEADER_ONLY = "en-tete";

    public static final String USAGE = """
            Usage : java -jar target/hai913i-tp1-analyzer.jar PROJET [ACTION] [options]

              PROJET   dossier du projet Java à analyser

              ACTION   (aucune)    unités de compilation et erreurs de compilation (A0)
                       ast         arbre syntaxique (A1)            option : --fichier Nom.java
                       structure   classes, attributs, méthodes (A2) option : --fichier Nom.java
                       appels      appels et type statique du receveur (A3) option : --fichier Nom.java
                       faits       comptages relus depuis le modèle de faits (B1)
                       metriques   les 13 métriques (B2)            exige  : --x N
                       graphe      graphe d'appel (B3)              options: --constructeurs
                                                                             --methode TEXTE
                                                                             --dot FICHIER
                                                                             --image FICHIER
                                                                             --paquetage NOM
                                                                             --voisinage TEXTE
                                                                             --sans-isoles
                                                                             --par-classe
                       tout        structure, appels, métriques et graphe (exige --x N)
                       menu        menu interactif
                       comparer    comptages de Spoon et écarts avec JDT (C1)

              --x N             seuil X : classes ayant strictement plus de N méthodes (entier >= 0)
              --fichier NOM     n'afficher que l'AST (ast), les types (structure) ou les appels
                                (appels) de ce fichier
              --constructeurs   ajouter au graphe les arcs vers les constructeurs
              --methode TEXTE   appelées et appelantes des méthodes dont la clé contient TEXTE
              --dot FICHIER     écrire le graphe au format DOT dans FICHIER
              --image FICHIER   produire l'image du graphe avec Graphviz (.svg, .png ou .pdf)
              --paquetage NOM   ne dessiner que les méthodes de ce paquetage
              --voisinage TEXTE ne dessiner que les méthodes dont la clé contient TEXTE,
                                leurs appelantes et leurs appelées
              --sans-isoles     ne pas dessiner les méthodes sans aucun arc
              --par-classe      regrouper les méthodes dans un cadre par classe
              --moteur NOM      extraire les faits avec jdt (par défaut) ou avec spoon
              -h, --help        afficher cette aide

            Exemple : java -jar target/hai913i-tp1-analyzer.jar "../mon projet" metriques --x 5
            """;

    /**
     * @throws IllegalArgumentException avec un message explicite si les arguments sont invalides
     */
    public static Options parse(String[] args) {
        if (args.length < 1) {
            throw new IllegalArgumentException("Il faut au moins le dossier du projet.");
        }

        Path project = Path.of(args[0]);
        if (args.length == 1) {
            return new Options(project, HEADER_ONLY, null, null, false, null, null, JDT, null, GraphView.Filter.NONE);
        }
        String action = args[1];
        if (!ACTIONS.contains(action)) {
            throw new IllegalArgumentException("Action inconnue : « " + action + " ». "
                    + "Actions possibles : " + String.join(", ", ACTIONS) + ".");
        }

        Integer x = null;
        String file = null;
        boolean constructorArcs = false;
        String method = null;
        Path dot = null;
        String engine = JDT;
        Path image = null;
        String packageName = null;
        String focus = null;
        boolean hideIsolated = false;
        boolean byClass = false;

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--x" -> x = parseX(valueOf(args, ++i, "--x"));
                case "--fichier" -> file = valueOf(args, ++i, "--fichier");
                case "--constructeurs" -> constructorArcs = true;
                case "--methode" -> method = valueOf(args, ++i, "--methode");
                case "--dot" -> dot = Path.of(valueOf(args, ++i, "--dot"));
                case "--image" -> image = parseImage(valueOf(args, ++i, "--image"));
                case "--paquetage" -> packageName = valueOf(args, ++i, "--paquetage");
                case "--voisinage" -> focus = valueOf(args, ++i, "--voisinage");
                case "--sans-isoles" -> hideIsolated = true;
                case "--par-classe" -> byClass = true;
                case "--moteur" -> engine = parseEngine(valueOf(args, ++i, "--moteur"));
                default -> throw new IllegalArgumentException("Option inconnue : « " + args[i] + " ».");
            }
        }

        if (x == null && (action.equals("metriques") || action.equals("tout"))) {
            throw new IllegalArgumentException("Le seuil X est obligatoire pour l'action « " + action
                    + " » : ajoutez --x N (entier positif ou nul).");
        }

        if (engine.equals(SPOON) && action.equals("ast")) {
            throw new IllegalArgumentException("L'action « ast » affiche l'arbre de JDT : "
                    + "elle n'existe pas avec --moteur spoon.");
        }

        GraphView.Filter view = new GraphView.Filter(packageName, focus, hideIsolated, byClass);
        if (!view.isNone() && dot == null && image == null) {
            throw new IllegalArgumentException("Les options --paquetage, --voisinage, --sans-isoles et --par-classe "
                    + "choisissent ce qui est dessiné : ajoutez --dot FICHIER ou --image FICHIER.");
        }

        return new Options(project, action, x, file, constructorArcs, method, dot, engine, image, view);
    }

    public static final List<String> IMAGE_FORMATS = List.of("svg", "png", "pdf");

    // Le format de l'image est celui de l'extension du fichier
    private static Path parseImage(String text) {
        String extension = text.contains(".") ? text.substring(text.lastIndexOf('.') + 1).toLowerCase() : "";
        if (!IMAGE_FORMATS.contains(extension)) {
            throw new IllegalArgumentException("Format d'image inconnu : « " + text + " ». "
                    + "Extensions possibles : .svg, .png, .pdf.");
        }
        return Path.of(text);
    }

    private static String parseEngine(String text) {
        String engine = text.trim().toLowerCase();
        if (engine.equals(JDT) || engine.equals(SPOON)) return engine;
        throw new IllegalArgumentException("Moteur inconnu : « " + text + " ». Moteurs possibles : jdt, spoon.");
    }

    /**
     * @throws IllegalArgumentException si le texte n'est pas un entier positif ou nul
     */
    public static int parseX(String text) {
        try {
            int value = Integer.parseInt(text.trim());
            if (value >= 0) return value;
        } catch (NumberFormatException e) {
            // même refus que pour une valeur négative, ci-dessous
        }
        throw new IllegalArgumentException("Seuil X invalide : « " + text + " ». "
                + "Un entier positif ou nul est attendu.");
    }

    private static String valueOf(String[] args, int index, String option) {
        if (index >= args.length) {
            throw new IllegalArgumentException("L'option " + option + " attend une valeur.");
        }
        return args[index];
    }
}
