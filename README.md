# HAI913I — TP1 — Analyseur statique de programmes Java

Outil en ligne de commande qui lit le code source d'un projet Java, en extrait la structure et les
appels de méthodes, calcule 13 métriques et construit le graphe d'appel. Il s'appuie sur Eclipse JDT.

## Projet

### Entrée et sorties

- **Entrée** : le dossier d'un projet Java 17 (sources seules, pas de `module-info.java`). La racine
  des sources est `src/main/java` si ce dossier existe, sinon `src`, sinon le dossier lui-même.
- **Sorties** : du texte sur la sortie standard, et, sur demande, un fichier DOT (Graphviz) du graphe
  d'appel. Les messages d'erreur vont sur la sortie d'erreur.

### Architecture

Un seul module Maven. La chaîne suit le CM 02 : fichiers → AST et bindings → visiteurs → modèle de
faits → métriques et graphe → affichage.

| Paquetage | Rôle |
|---|---|
| `hai913i.tp1` | `Main` lit les arguments et délègue ; `Analysis` exécute la chaîne une fois |
| `cli` | `Options` (lecture et validation des arguments), `InteractiveMenu` |
| `parse` | `ProjectSources` (racine et fichiers), `JdtParser` (configuration d'`ASTParser`) |
| `visitors` | visiteurs JDT : arbre, structure, appels, comptages ; `CountingRules` (§ 4.1 du sujet) |
| `model.infos` | objets remplis par les visiteurs |
| `model.facts` | modèle de faits, sans aucune classe JDT |
| `utils` | `FactBuilder` (construit les faits), `MethodSignatureUtil` (clé d'une méthode) |
| `analysis` | `MetricsService` (13 métriques), `CallGraph`, `DotExporter` : sur les faits seuls |
| `report` | `ConsoleReport` : tout l'affichage texte |

### Dépendances

- Eclipse JDT Core `org.eclipse.jdt:org.eclipse.jdt.core:3.46.0` (analyse) ;
- JUnit Jupiter 5.14.4 (tests).

Maven les télécharge ; aucun JAR n'est à installer à la main. Spoon n'est pas utilisé.

### Approche JDT

`ASTParser` analyse tous les fichiers en un seul lot, avec résolution des bindings, pour que les
appels d'un fichier vers un autre soient résolus. Les superclasses, le type statique du receveur et
la cible d'un appel viennent des bindings ; les membres d'un type sont lus dans `bodyDeclarations()`.

### Limites connues

- Le graphe d'appel est une approximation statique : pas d'expansion des appels virtuels, pas de
  réflexion ni d'injection de dépendances. Les références de méthode (`Type::m`) ne sont pas des appels.
- Les bibliothèques du projet analysé ne sont pas fournies au parseur : leurs appels sont signalés
  « non résolue ».
- Un fichier qui ne compile pas est analysé partiellement.
- Avec `--constructeurs`, un `new` vers une classe sans constructeur écrit, ou vers une classe
  anonyme, est compté comme appel externe.

## Environnement

| Élément | Version utilisée |
|---|---|
| JDK | OpenJDK Temurin 21.0.5 (Java 17 au minimum) |
| Maven | 3.9.9 |
| Eclipse JDT Core | 3.46.0 |
| JUnit Jupiter | 5.14.4 |
| Système | macOS 27.0 |
| IDE | IntelliJ IDEA (version à compléter) ; aucun élément ne dépend de l'IDE |
| Graphviz | facultatif, pour convertir le fichier DOT en image (version à compléter si utilisé) |

## Construction

Depuis une copie fraîche du dépôt, à la racine du projet :

```text
mvn clean package
```

La commande compile, lance les tests et produit `target/hai913i-tp1-analyzer.jar`, un JAR exécutable
qui contient ses dépendances.

## Exécution

```text
java -jar target/hai913i-tp1-analyzer.jar PROJET ACTION [options]
```

| Action | Ce qu'elle affiche | Options |
|---|---|---|
| `ast` | l'arbre syntaxique (A1) | `--fichier Nom.java` |
| `structure` | classes, attributs, méthodes, héritage (A2) | |
| `appels` | appels, type statique du receveur, cible (A3) | |
| `faits` | les comptages relus depuis le modèle de faits (B1) | |
| `metriques` | les 13 métriques (B2) | `--x N` obligatoire |
| `graphe` | le graphe d'appel (B3) | `--constructeurs`, `--methode TEXTE`, `--dot FICHIER` |
| `tout` | structure, appels, métriques et graphe | `--x N` obligatoire |
| `menu` | un menu interactif donnant accès aux mêmes affichages | |

- `--x N` : seuil X de la question 11, entier positif ou nul.
- `--methode TEXTE` : appelées et appelantes des méthodes dont la clé contient `TEXTE`.
- `--dot FICHIER` : écrit le graphe au format DOT.
- `-h` ou `--help` : affiche l'aide.

Codes de sortie : `0` succès ; `2` arguments invalides ; `3` analyse impossible (dossier introuvable,
aucun fichier Java) ; `1` erreur inattendue.

### Exemples

```text
java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation ast --fichier Dvd.java
java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation structure
java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation appels
java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation metriques --x 5
java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation graphe --methode Loanable#checkOut
java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation graphe --constructeurs --dot callgraph.dot
java -jar target/hai913i-tp1-analyzer.jar "/chemin/avec des espaces/projet" tout --x 4
```

Conversion du fichier DOT en image, si Graphviz est installé :

```text
dot -Tsvg callgraph.dot -o callgraph.svg
```

## Validation

Le projet de validation du sujet est copié dans `src/test/resources/validation`, et les échantillons
de robustesse dans `src/test/resources/robustness`.

`mvn clean test` rejoue tous les points de contrôle ; chaque classe de test porte le nom de son étape
(`ParserSmokeTest` pour A0, puis `ControlPointA1Test` à `ControlPointB4Test`).

Valeurs attendues sur le projet de validation :

| Étape | Commande (après `PROJET` = `src/test/resources/validation`) | Résultat attendu |
|---|---|---|
| A0 | toute action | 14 unités de compilation, 0 erreur |
| A2 | `structure` | 15 classes : 12 classes, 2 interfaces, 1 énumération ; 59 méthodes, dont 9 constructeurs ; 21 attributs |
| A3 | `appels` | 94 appels : 46 vers le projet, 48 vers l'extérieur, 0 non résolu |
| B1 | `faits` | les mêmes nombres, relus depuis le modèle |
| B2 | `metriques --x 5` | 15 classes, 493 lignes, 59 méthodes, 4 paquetages ; 3,93 ; 4,64 ; 1,40 ; 3 classes pour X = 5 |
| B3 | `graphe` | 59 nœuds, 44 arcs, 46 sites internes, 48 externes, 0 non résolu |
| B3 | `graphe --constructeurs` | 52 arcs, 56 sites |

Robustesse (B4) :

| Commande | Résultat attendu |
|---|---|
| `... dossier/inexistant structure` | « Dossier introuvable », code 3 |
| `... src/test/resources/robustness/no-java-files structure` | « Aucun fichier .java », code 3 |
| `... src/test/resources/robustness/syntax-error structure` | 2 erreurs signalées dans `Broken.java`, `Helper` analysé, code 0 |
| `... src/test/resources/robustness/missing-dep appels` | `capitalize` non résolue, `decorate` résolue, code 0 |
| `... src/test/resources/validation metriques --x abc` | « Seuil X invalide », code 2 |

## Dépannage

| Symptôme | Cause et remède |
|---|---|
| `Unable to access jarfile target/...` | le JAR n'est pas construit : lancer `mvn clean package` depuis la racine du projet |
| `UnsupportedClassVersionError` | Java trop ancien : il faut Java 17 ou plus (`java -version`) |
| `Dossier introuvable` | le chemin est relatif au répertoire courant ; entourer de guillemets un chemin contenant des espaces |
| `Le seuil X est obligatoire` | ajouter `--x N` aux actions `metriques` et `tout` |
| Beaucoup d'appels « non résolue » | le projet analysé dépend de bibliothèques qui ne sont pas fournies au parseur |
| Accents illisibles sous Windows | lancer avec `java -Dstdout.encoding=UTF-8 -jar ...` |
| `dot: command not found` | Graphviz n'est pas installé ; le fichier DOT reste lisible comme texte |
