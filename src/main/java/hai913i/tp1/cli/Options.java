package hai913i.tp1.cli;

import java.nio.file.Path;
import java.util.List;

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
        Path dot                   // --dot : fichier DOT à écrire
) {

    public static final List<String> ACTIONS =
            List.of("ast", "structure", "appels", "faits", "metriques", "graphe", "tout", "menu");

    public static final String USAGE = """
            Usage : java -jar target/hai913i-tp1-analyzer.jar PROJET ACTION [options]

              PROJET   dossier du projet Java à analyser

              ACTION   ast         arbre syntaxique (A1)            option : --fichier Nom.java
                       structure   classes, attributs, méthodes (A2)
                       appels      appels et type statique du receveur (A3)
                       faits       comptages relus depuis le modèle de faits (B1)
                       metriques   les 13 métriques (B2)            exige  : --x N
                       graphe      graphe d'appel (B3)              options: --constructeurs
                                                                             --methode TEXTE
                                                                             --dot FICHIER
                       tout        structure, appels, métriques et graphe (exige --x N)
                       menu        menu interactif

              --x N             seuil X : classes ayant strictement plus de N méthodes (entier >= 0)
              --fichier NOM     n'afficher que l'AST de ce fichier
              --constructeurs   ajouter au graphe les arcs vers les constructeurs
              --methode TEXTE   appelées et appelantes des méthodes dont la clé contient TEXTE
              --dot FICHIER     écrire le graphe au format DOT dans FICHIER
              -h, --help        afficher cette aide

            Exemple : java -jar target/hai913i-tp1-analyzer.jar "../mon projet" metriques --x 5
            """;

    /**
     * @throws IllegalArgumentException avec un message explicite si les arguments sont invalides
     */
    public static Options parse(String[] args) {
        if (args.length < 2) {
            throw new IllegalArgumentException("Il faut au moins le dossier du projet et une action.");
        }

        Path project = Path.of(args[0]);
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

        for (int i = 2; i < args.length; i++) {
            switch (args[i]) {
                case "--x" -> x = parseX(valueOf(args, ++i, "--x"));
                case "--fichier" -> file = valueOf(args, ++i, "--fichier");
                case "--constructeurs" -> constructorArcs = true;
                case "--methode" -> method = valueOf(args, ++i, "--methode");
                case "--dot" -> dot = Path.of(valueOf(args, ++i, "--dot"));
                default -> throw new IllegalArgumentException("Option inconnue : « " + args[i] + " ».");
            }
        }

        if (x == null && (action.equals("metriques") || action.equals("tout"))) {
            throw new IllegalArgumentException("Le seuil X est obligatoire pour l'action « " + action
                    + " » : ajoutez --x N (entier positif ou nul).");
        }

        return new Options(project, action, x, file, constructorArcs, method, dot);
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
