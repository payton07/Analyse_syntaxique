= Partie A0 : Environnement et validation
== 1- verification des version de java et maven
== 2- compilation reussie
== 3-
== 4 - analyse du projet validation :
=== resultat :
        Racine des sources     : /Users/payton/Documents/FAC/M2_GL/Semestre1/Evolution-Restruct/TDS-TPS/TP1/ressources/validation/src
        Unites de compilation  : 14
        Erreurs de compilation : 0

= Partie A1 : Parcourir AST
== 1- Classe visitor crée et nommée TreeVisitor
== 2 - Classe => TypeDeclaration
    Attribut => FieldDeclaration
    Method => MethodDeclaration
    appel => MethodInvocation

== 3 - Rôle de la valeur de visit(...) => On descend dans l'arbre en focntion de la valeur de visit(...). True => on descend et False => Non on ne descend pas.


= Partie A2 : Extraire la structure du projet
1 -

= Partie A3 : Extraire les appels et le type statique du receveur

= Partie B1 : Un modèle de faits indépendant de JDT

Question 3 : Justification des choix ce representations :

#figure(
  table(
    columns: (1.5fr, 3fr),
    stroke: 0.5pt + luma(150),
    fill: (col, row) => if row == 0 { rgb("f0f2f5") },
    align: (left, left),
    table.header(
      [*Choix de modélisation*],
      [*Justification technique*]
    ),
    [Identifiant de méthode (`methodKey`)],
    [Format `QualifiedClassName#methodName(ErasureType1,...)`. Garantit l'unicité globale et l'alignement avec les conventions de graphe d'appel. Les constructeurs sont unifiés sous l'identifiant `<init>`.],

    [Traitement des surcharges],
    [La signature intègre la liste ordonnée des types statiques effacés (_type erasure_) des paramètres. Deux méthodes homonymes possèdent donc des clés distinctes sans risque de collision.],

    [Classes imbriquées],
    [Identifiées par leur nom qualifié complet (`Outer.Inner` ou `Outer$Inner`). Elles disposent de leur propre `TypeFact`, permettant un traitement uniforme avec les types de premier niveau.],

    [Appels non résolus],
    [Représentés par la cible sentinelle `"non résolue"`. Cela prévient tout `NullPointerException` dans les étapes suivantes tout en permettant un filtrage direct via `CallFact.isResolved()`.],

    [Déterminisme absolu],
    [Emploi exclusif de `TreeMap` et de listes triées implémentant `Comparable` pour garantir une reproductibilité stricte entre deux exécutions consécutives.]
  ),
  caption: [Justification des choix de représentation pour le modèle de faits (B1)]
)

= Partie B2 :

= Partie B3 : Graphe d'appel

== 1 - Ce que demande le sujet
Construire le graphe d'appel à partir du modèle de faits (aucune classe JDT), produire une sortie exploitable (arcs avec leur poids, nombres de nœuds, d'arcs, d'appels externes et non résolus) et pouvoir interroger le graphe dans les deux sens : appelées et appelantes d'une méthode.

== 2 - Étapes de réalisation

=== Étape 1 : garder les appels de constructeurs à part (option)
- *Quoi* : `CallExtractorVisitor` enregistre maintenant aussi `new T(...)`, `this(...)` et `super(...)` (nœuds `ClassInstanceCreation`, `ConstructorInvocation`, `SuperConstructorInvocation`), dans une seconde table `constructorCallsByMethod`. `MethodFact` reçoit une seconde liste `constructorCalls`.
- *Pourquoi* : le § 4.4 définit un appel comme un `MethodInvocation` ou un `SuperMethodInvocation`. Les appels de constructeurs ne font donc pas partie des 94 appels de A3 et ne doivent pas les fausser. Les arcs vers les constructeurs sont facultatifs et doivent être une option : il faut pouvoir les ajouter ou non sans réanalyser le code.
- *Comment* : deux listes séparées dans le modèle. `calls` sert à A3, B1 et au graphe par défaut ; `constructorCalls` n'est lue que si l'option est activée. La cible est calculée par la même fonction `MethodSignatureUtil.getMethodKey`, à partir de `resolveConstructorBinding()`.

=== Étape 2 : la classe `CallGraph`
- *Quoi* : une classe du paquetage `analysis`, construite à partir d'un `ProjectFacts` et d'un booléen (option constructeurs).
- *Pourquoi dans `analysis`* : comme `MetricsService`, elle exploite les faits sans relire le code. Elle n'importe aucune classe de `org.eclipse.jdt`, ce qui respecte B1.
- *Nœuds* : une entrée par méthode du modèle, créée avant de lire les appels. Une méthode qui n'appelle rien et que personne n'appelle reste donc un nœud (59 nœuds, constructeurs compris). L'identifiant est la clé de méthode, donc deux surcharges sont deux nœuds distincts.
- *Arcs* : chaque site d'appel est classé dans un seul des trois cas :
  - cible `non résolue` : compté dans les appels non résolus, pas d'arc ;
  - cible absente des méthodes du projet : compté dans les appels externes, pas d'arc ni de nœud ;
  - cible dans le projet : compté comme site interne, et l'arc appelant -> cible est créé ou son poids augmenté de 1.
- *Poids* : `Map.merge(cible, 1, Integer::sum)` crée l'arc avec le poids 1 ou ajoute 1 s'il existe déjà. Plusieurs sites de f vers g donnent ainsi un seul arc. La somme des poids est égale au nombre de sites internes (vérifié par un test).
- *Récursion* : aucun traitement particulier, l'appelant et la cible ont la même clé, d'où un arc f -> f.
- *Pas d'expansion des appels virtuels* : la cible est celle du binding, déjà enregistrée dans le fait. Un appel via `Loanable` vise `Loanable#checkOut` et non `Item#checkOut`. Le graphe ne fait aucune analyse de hiérarchie.

=== Étape 3 : les deux sens du graphe
- *Quoi* : deux tables, `callees` (appelant -> appelées) et `callers` (appelée -> appelantes), remplies en même temps.
- *Pourquoi* : la question « qui appelle g ? » est celle de l'analyse d'impact (CM 02). Avec une seule table, il faudrait parcourir tous les arcs à chaque question. Les remplir ensemble garantit qu'elles restent cohérentes (vérifié par un test).
- *Comment* : `getCallees(clé)` et `getCallers(clé)` renvoient les voisins avec le poids de l'arc. `findNodes(texte)` retrouve les méthodes dont la clé contient un texte, pour ne pas avoir à saisir une clé complète.

=== Étape 4 : déterminisme
Toutes les tables sont des `TreeMap` : les nœuds et les arcs sortent dans l'ordre alphabétique des clés. Deux exécutions donnent exactement les mêmes arcs et le même fichier DOT (vérifié par un test).

=== Étape 5 : les sorties
- *Texte (console)* : les cinq compteurs, puis un arc par ligne sous la forme `appelant -> appelée [poids]`. Lisible sans outil, facile à comparer d'une exécution à l'autre et à filtrer avec `grep`.
- *DOT (fichier)* : écrit par `DotExporter`, séparé de `CallGraph` pour ne pas mélanger le calcul et la mise en forme. DOT est un format texte standard, qui représente directement des arcs orientés et étiquetés, et que Graphviz convertit en image. L'identifiant d'un nœud est la clé complète (unique) ; son étiquette est une forme courte sans les paquetages (`Catalog#add(Item)`). Le poids n'est écrit que s'il dépasse 1.
- *Menu* : option 7 de `MainCLI`. Elle demande si l'on ajoute les arcs vers les constructeurs, affiche les compteurs et les arcs, puis propose d'interroger une méthode et d'exporter le fichier DOT.

== 3 - Résultats sur le projet de validation
#table(
  columns: (2fr, 1fr, 1fr),
  stroke: 0.5pt + luma(150),
  table.header([*Mesure*], [*Attendu*], [*Obtenu*]),
  [Nœuds], [59], [59],
  [Arcs], [44], [44],
  [Sites d'appel internes], [46], [46],
  [Appels externes], [48], [48],
  [Appels non résolus], [0], [0],
  [Arcs avec l'option constructeurs], [52], [52],
  [Sites avec l'option constructeurs], [56], [56],
)

- `Catalog#add(Item,Item)` -> `Catalog#add(Item)` : poids 2.
- `TextUtils#repeat(String,int)` : arc vers lui-même.
- Appelantes de `Loanable#checkOut(Member)` : `LoanService#borrow` seulement.
- Appelantes de `Item#checkOut(Member)` : aucune.

== 4 - Validation
Le test `ControlPointB3Test` vérifie chaque ligne du point de contrôle, la cohérence des deux sens, l'égalité entre la somme des poids et le nombre de sites internes, le déterminisme et le contenu du fichier DOT.

== 5 - Limites
- Le graphe est une approximation statique : il répond à « f nomme-t-il g dans son code ? ». Il ignore la réflexion, les proxys et l'injection de dépendances, et ne dit pas quelle redéfinition sera exécutée. `Item#checkOut` n'a aucune appelante dans le graphe alors qu'elle est bien exécutée quand `LoanService#borrow` appelle `Loanable#checkOut`.
- Les références de méthode (`Type::m`) ne sont pas des appels (§ 4.4) et ne donnent aucun arc.
- Avec l'option constructeurs, un `new` vers une classe sans constructeur écrit (constructeur par défaut) ou vers une classe anonyme est compté comme externe : sa cible n'est pas une méthode comptée du projet.
- Les appels de constructeurs implicites (`super()` non écrit) n'apparaissent pas : ils n'ont pas de nœud dans l'AST.


= Partie B4 : Un outil utilisable et reproductible

== 1 - Ce que demande le sujet
Une ligne de commande qui reçoit le chemin du projet, l'action et le seuil X ; des cas d'erreur traités sans pile d'exception, avec un code de sortie non nul quand l'analyse est impossible ; une construction reproductible depuis une copie fraîche.

== 2 - Étapes de réalisation

=== Étape 1 : la syntaxe de la ligne de commande
- *Quoi* : `PROJET ACTION` suivis d'options. Actions : `ast`, `structure`, `appels`, `faits`, `metriques`, `graphe`, `tout`, `menu`. Options : `--x N`, `--fichier`, `--constructeurs`, `--methode`, `--dot`.
- *Pourquoi* : une action par étape du sujet, ce qui rend chaque point de contrôle reproductible par une seule commande. Le projet et l'action sont obligatoires, donc positionnels ; le reste dépend de l'action, donc en options nommées.
- *Comment* : la classe `Options` (un record) lit et valide les arguments avant toute analyse. Elle lève `IllegalArgumentException` avec un message explicite. Elle contient aussi le texte d'usage.

=== Étape 2 : le seuil X
- *Quoi* : `--x N` est obligatoire pour `metriques` et `tout`. Une valeur absente, non entière ou négative est refusée.
- *Pourquoi* : le § 4.3 impose X sur la ligne de commande et le refus d'une valeur mal formée. Refuser avant l'analyse évite de calculer pour rien.
- *Comment* : `Options.parseX` est la seule fonction qui valide X. Le menu interactif l'utilise aussi, ce qui garantit la même règle dans les deux modes.

=== Étape 3 : une chaîne d'analyse unique
- *Quoi* : le record `Analysis` exécute la chaîne une fois (sources, AST, visiteurs, faits) et garde le résultat.
- *Pourquoi* : auparavant, le menu relançait les visiteurs à chaque option et les tests refaisaient la même séquence. Une seule entrée garantit que toutes les actions lisent les mêmes faits.
- *Comment* : `Analysis.of(chemin)` trie les fichiers, relève les erreurs de compilation, lance les trois visiteurs et appelle `FactBuilder`.

=== Étape 4 : séparer l'affichage
- *Quoi* : `ConsoleReport` contient tout l'affichage, une méthode par action. L'ancienne classe `MainCLI` (menu, construction des faits et affichage mêlés) est remplacée par `InteractiveMenu`, qui ne fait qu'appeler `ConsoleReport`.
- *Pourquoi* : l'indice du sujet demande des composants séparés et testables. `Main` lit les arguments et délègue ; les calculs restent dans `analysis` ; l'affichage ne calcule rien.
- *Comment* : `ConsoleReport` reçoit le flux de sortie en paramètre. Les tests lui donnent un flux en mémoire et lisent ce qui a été écrit.

=== Étape 5 : les erreurs et les codes de sortie
#table(
  columns: (2fr, 2fr, 1fr),
  stroke: 0.5pt + luma(150),
  table.header([*Cas*], [*Comportement*], [*Code*]),
  [Arguments invalides (action inconnue, X absent ou mal formé)], [message et usage], [2],
  [Dossier introuvable, dossier sans fichier Java], [message], [3],
  [Fichier qui ne compile pas], [erreurs listées, analyse poursuivie], [0],
  [Binding non résolu], [appel marqué « non résolue » et compté], [0],
  [Erreur imprévue], [message d'une ligne, sans pile], [1],
)
- *Pourquoi deux codes* : un script appelant peut distinguer une mauvaise commande d'un projet inanalysable.
- *Comment* : `Main.run` renvoie le code de sortie au lieu d'appeler `System.exit`, ce qui permet de le tester. `main` se réduit à `System.exit(run(...))`. Le contrôle « aucun fichier .java » est dans `ProjectSources.of`.
- *Erreurs de syntaxe* : `setStatementsRecovery(true)` a été ajouté à `JdtParser`. Sans lui, le corps d'une méthode contenant une erreur est vide et ses appels sont perdus ; avec lui, l'appel `Helper.add` de `Broken.java` est retrouvé.

=== Étape 6 : reproductibilité
- Les échantillons de robustesse sont copiés dans `src/test/resources/robustness` : les tests ne dépendent plus d'un dossier extérieur au dépôt. Le cas `missing-dep` du test A3, jusque-là sauté en silence, est maintenant exécuté.
- Vérifié sur une copie du projet sans `target/`, placée dans un dossier dont le nom contient un espace : `mvn clean package` réussit et le JAR s'exécute.
- Deux exécutions de l'action `tout` donnent une sortie identique.

== 3 - Résultats sur le point de contrôle
- Chemin inexistant : « Dossier introuvable », code 3.
- `no-java-files` : « Aucun fichier .java », code 3.
- `syntax-error` : 2 erreurs signalées dans `Broken.java`, `Helper` analysé, code 0.
- `--x abc` : « Seuil X invalide », code 2.

== 4 - Validation
`ControlPointB4Test` appelle `Main.run` avec les arguments de chaque cas et vérifie le code de sortie, le message, l'absence de pile d'exception, le chemin avec espaces, l'application de X et le déterminisme.
