# HAI913I — TP1 — Analyseur statique de programmes Java

Outil en ligne de commande qui lit le code source d'un projet Java, en extrait la structure et les
appels de méthodes, calcule 13 métriques et construit le graphe d'appel. Il s'appuie sur Eclipse JDT.
En bonus (partie C1), la même extraction peut être faite avec Spoon et comparée à celle de JDT.

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
| `spoon` | bonus C1 : `SpoonModel`, `SpoonFactExtractor` (mêmes faits, extraits avec Spoon), `FactComparison`, `EngineComparison` |
| `analysis` | `MetricsService` (13 métriques), `CallGraph`, `DotExporter`, `GraphView` (bonus C2 : ce qui est dessiné) : sur les faits seuls |
| `report` | `ConsoleReport` : tout l'affichage texte ; `GraphvizRenderer` (bonus C2) : appelle Graphviz pour produire une image |

### Dépendances

- Eclipse JDT Core `org.eclipse.jdt:org.eclipse.jdt.core:3.46.0` (analyse) ;
- Spoon `fr.inria.gforge.spoon:spoon-core:11.5.0` (bonus C1), qui s'appuie sur la même version de JDT ;
- SLF4J `org.slf4j:slf4j-nop:2.0.18`, pour supprimer l'avertissement de journalisation de Spoon ;
- JUnit Jupiter 5.14.4 (tests).

Maven les télécharge ; aucun JAR n'est à installer à la main.

### Approche JDT

`ASTParser` analyse tous les fichiers en un seul lot, avec résolution des bindings, pour que les
appels d'un fichier vers un autre soient résolus. Les superclasses, le type statique du receveur et
la cible d'un appel viennent des bindings ; les membres d'un type sont lus dans `bodyDeclarations()`.

### Approche Spoon (bonus C1)

`SpoonFactExtractor` remplit le même modèle de faits que la chaîne JDT, à partir du modèle construit
par le `Launcher` de Spoon en mode `noClasspath`. Il applique les conventions du sujet, que Spoon ne
suit pas : constructeurs par défaut et appels `super()` implicites écartés, constantes d'énumération
non comptées comme attributs, types imbriqués nommés avec un point. Une cible d'appel n'est déclarée
résolue que si le type qui la déclare est dans les sources analysées ou dans le JDK.

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
| Spoon | 11.5.0 (bonus C1) |
| JUnit Jupiter | 5.14.4 |
| Système | macOS 27.0 |
| IDE | IntelliJ IDEA (version à compléter) ; aucun élément ne dépend de l'IDE |
| Graphviz | 16.1.0 ; facultatif, nécessaire seulement pour l'option `--image` (bonus C2) |

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
| `graphe` | le graphe d'appel (B3) et son dessin (C2) | `--constructeurs`, `--methode TEXTE`, `--dot FICHIER`, `--image FICHIER`, `--paquetage NOM`, `--voisinage TEXTE`, `--sans-isoles`, `--par-classe` |
| `tout` | structure, appels, métriques et graphe | `--x N` obligatoire |
| `menu` | un menu interactif donnant accès aux mêmes affichages | |
| `comparer` | comptages bruts de Spoon, et écarts entre les faits de Spoon et ceux de JDT (C1) | |

- `--x N` : seuil X de la question 11, entier positif ou nul.
- `--methode TEXTE` : appelées et appelantes des méthodes dont la clé contient `TEXTE`.
- `--dot FICHIER` : écrit le graphe au format DOT.
- `--image FICHIER` : produit l'image du graphe avec Graphviz. L'extension donne le format : `.svg`, `.png`
  ou `.pdf`.
- `--paquetage NOM` : ne dessine que les méthodes de ce paquetage et les arcs entre elles.
- `--voisinage TEXTE` : ne dessine que les méthodes dont la clé contient `TEXTE`, leurs appelantes et leurs
  appelées.
- `--sans-isoles` : ne dessine pas les méthodes sans aucun arc.
- `--par-classe` : regroupe les méthodes dans un cadre par classe, coloré selon le paquetage.

Ces quatre dernières options choisissent ce qui est dessiné dans le fichier DOT et dans l'image ; elles
exigent `--dot` ou `--image`. Elles ne changent ni les compteurs ni la liste des arcs affichés.
- `--moteur jdt` ou `--moteur spoon` : analyseur qui remplit le modèle de faits (`jdt` par défaut). Vaut pour
  toutes les actions sauf `ast`, qui affiche l'arbre de JDT.
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
java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation comparer
java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation graphe --moteur spoon
```

Images du graphe (bonus C2), si Graphviz est installé :

```text
java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation graphe --par-classe --sans-isoles --image graphe.svg
java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation graphe --voisinage "LoanService#borrow" --par-classe --image voisinage.png
java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation graphe --paquetage library.service --par-classe --dot service.dot
```

Un fichier DOT peut aussi être converti à la main : `dot -Tsvg callgraph.dot -o callgraph.svg`.

## Validation

Le projet de validation du sujet est copié dans `src/test/resources/validation`, et les échantillons
de robustesse dans `src/test/resources/robustness`.

`mvn clean test` rejoue tous les points de contrôle ; chaque classe de test porte le nom de son étape
(`ParserSmokeTest` pour A0, puis `ControlPointA1Test` à `ControlPointB4Test`, puis `ControlPointC1Test` et `ControlPointC2Test`).

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
| C1 | `comparer` | Spoon brut : 14 ou 15 types, 50 méthodes et 13 constructeurs, 24 attributs, 6 paquetages, 107 appels ; 0 écart entre les deux modèles de faits |
| C1 | `graphe --moteur spoon` | 59 nœuds, 44 arcs, 46 sites, comme avec JDT |
| C2 | `graphe --par-classe --sans-isoles --image graphe.svg` | « Vue dessinée : 36 nœuds sur 59, 44 arcs sur 44 », et le fichier `graphe.svg` |
| C2 | `graphe --voisinage "LoanService#borrow" --image v.svg` | « Vue dessinée : 9 nœuds sur 59, 8 arcs sur 44 » |

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
| `Graphviz est introuvable` | Graphviz n'est pas installé ou n'est pas dans le `PATH` : l'installer, ou remplacer `--image` par `--dot` |
