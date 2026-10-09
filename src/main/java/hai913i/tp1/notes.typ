= Partie A0 : Environnement et validation
== 1 - Vérification des versions de Java et de Maven
`java -version` : OpenJDK Temurin 21.0.5 (Java 17 au minimum). `mvn -v` : Maven 3.9.9.

== 2 - Compilation et exécution du projet de validation
Dans le dossier du projet de validation, `mvn -q compile exec:java` : compilation réussie.

== 3 - Création du projet d'analyseur
Départ du _starter_ fourni : projet Maven dépendant de JDT Core 3.46.0, avec `JdtParser` qui configure `ASTParser` pour résoudre les bindings.

== 4 - Analyse du projet de validation
Le projet de validation est copié dans `src/test/resources/validation`, pour que les tests et les commandes du README fonctionnent sur une copie fraîche du dépôt.

```text
$ mvn clean package
$ java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation faits
Racine des sources     : .../starter/src/test/resources/validation/src
Unites de compilation  : 14
Erreurs de compilation : 0
```

Ces trois lignes sont affichées en tête de chaque action. `ParserSmokeTest` vérifie en plus qu'un appel d'un fichier vers une méthode déclarée dans un autre fichier est résolu.

= Partie A1 : Parcourir un AST

== 1 - Ce que demande le sujet
Écrire un visiteur qui affiche l'arbre d'un fichier (le type de chaque nœud, indenté selon sa profondeur), repérer dans `Dvd.java` et `Category.java` les nœuds de la classe, des attributs, des méthodes et d'un appel, et expliquer le rôle de la valeur renvoyée par `visit(...)`.

== 2 - Étapes de réalisation

=== Étape 1 : le visiteur `TreeVisitor`
- *Quoi* : une sous-classe d'`ASTVisitor` qui affiche une ligne par nœud.
- *Pourquoi `preVisit` et `postVisit`* : `ASTVisitor` offre une méthode `visit` par type de nœud, soit plus de quatre-vingts. `preVisit(ASTNode)` et `postVisit(ASTNode)` sont appelées pour tous les nœuds, quel que soit leur type : deux méthodes suffisent pour suivre l'arbre entier.
- *Comment* : un compteur `depth`. `preVisit` écrit deux espaces par niveau puis le nom du type du nœud, et ajoute 1 ; `postVisit`, appelée quand le sous-arbre du nœud est terminé, retire 1. Le nom vient de `ASTNode.nodeClassForType(node.getNodeType()).getSimpleName()`.
- *Lien avec le patron Visitor* : on ne parcourt pas l'arbre soi-même. `unit.accept(visiteur)` lance le parcours, et chaque nœud rappelle la méthode du visiteur qui correspond à son type.

=== Étape 2 : lancer l'affichage
```text
java -jar target/hai913i-tp1-analyzer.jar src/test/resources/validation ast --fichier Dvd.java
```
Le flux de sortie est donné au visiteur à sa construction, ce qui permet de l'utiliser aussi dans un test.

== 3 - Lecture de l'arbre de `Dvd.java`
Extrait de la sortie (les nœuds de détail sont retirés) :
```text
CompilationUnit
  PackageDeclaration
  TypeDeclaration                  la classe Dvd
    SimpleType                     extends Item
    FieldDeclaration               private final int minutes;
      VariableDeclarationFragment
    MethodDeclaration              le constructeur Dvd(String, String, int)
      SingleVariableDeclaration    un nœud par paramètre
      Block
        SuperConstructorInvocation super(id, title, Category.FILM);
    MethodDeclaration              getMinutes()
    MethodDeclaration              dailyRate()
      MarkerAnnotation             @Override
```

#table(
  columns: (1fr, 2fr, 2fr),
  stroke: 0.5pt + luma(150),
  table.header([*Élément*], [*Nœud*], [*Remarque*]),
  [Classe], [`TypeDeclaration`], [sert aussi pour une interface (`isInterface()`)],
  [Attribut], [`FieldDeclaration`], [contient un `VariableDeclarationFragment` par variable déclarée],
  [Méthode], [`MethodDeclaration`], [le constructeur en est une aussi (`isConstructor()`)],
  [Appel], [`SuperConstructorInvocation`], [`Dvd.java` ne contient aucun `MethodInvocation` : son seul appel est `super(...)`],
)

Un appel ordinaire (`a.m()`, `m()`) est un `MethodInvocation`, et `super.m()` un `SuperMethodInvocation` ; on les trouve dans les autres fichiers du projet.

== 4 - Lecture de l'arbre de `Category.java`
```text
CompilationUnit
  PackageDeclaration
  EnumDeclaration                  l'énumération Category
    EnumConstantDeclaration        une par constante (3)
    FieldDeclaration
    MethodDeclaration              le constructeur
    MethodDeclaration
```
La racine du type est un `EnumDeclaration` et non un `TypeDeclaration`. Un visiteur qui ne redéfinit que `visit(TypeDeclaration)` ne voit donc pas cette énumération. Les constantes sont des `EnumConstantDeclaration`, distinctes des `FieldDeclaration`. Ces deux observations servent à l'étape A2.

== 5 - Rôle de la valeur renvoyée par `visit(...)`
- `visit(nœud)` est appelée à l'arrivée sur un nœud, avant ses enfants. Sa valeur décide si le parcours descend.
- *`return true`* : les enfants du nœud sont visités à leur tour. C'est la valeur par défaut d'`ASTVisitor`.
- *`return false`* : tout le sous-arbre du nœud est sauté. Le parcours reprend au nœud suivant, frère ou parent. `endVisit(nœud)` et `postVisit(nœud)` sont quand même appelées pour ce nœud.
- *Ce que cela changerait ici* : avec `return false` dans `visit(MethodDeclaration)`, le corps des méthodes ne serait plus parcouru et aucun appel ne serait trouvé. Avec `return false` dans `visit(TypeDeclaration)`, la classe membre `LoanService.Loan` ne serait jamais vue.
- *Choix retenu* : tous nos visiteurs renvoient `true`, pour atteindre les types imbriqués et les appels. Pour ne pas attribuer à un type les membres de ses types imbriqués, on ne coupe pas le parcours : on lit les membres directs dans `bodyDeclarations()` (étape A2).

== 6 - Validation
`ControlPointA1Test` vérifie que `Dvd.java` contient 1 `TypeDeclaration`, 1 `FieldDeclaration`, 3 `MethodDeclaration` dont 1 constructeur, et 1 `SuperConstructorInvocation` ; et que `Category.java` contient 1 `EnumDeclaration` et aucun `TypeDeclaration`.


= Partie A2 : Extraire la structure du projet

== 1 - Ce que demande le sujet
Pour chaque classe du projet (au sens du § 4.1) : son nom qualifié, son genre et son paquetage ; toutes ses superclasses et ses interfaces directes ; ses attributs (nom, type déclaré, visibilité) ; ses méthodes (nom, nombre de paramètres, constructeur ou non).

== 2 - Étapes de réalisation

=== Étape 1 : un visiteur qui voit tous les genres de types
- *Quoi* : `StructureVisitor` redéfinit `visit(TypeDeclaration)`, `visit(EnumDeclaration)` et `visit(RecordDeclaration)`. Les trois appellent la même méthode `addType`.
- *Pourquoi* : l'étape A1 a montré que `visit(TypeDeclaration)` ne voit ni les énumérations ni les records. Avec cette seule méthode, `Category` manquerait et on compterait 14 types au lieu de 15.
- *Comment* : les trois nœuds ont un supertype commun, `AbstractTypeDeclaration`, qui donne le nom, le binding et la liste des membres. Le genre (`Classe`, `Interface`, `Énumération`, `Record`) est fixé par la méthode `visit` appelante ; `TypeDeclaration.isInterface()` distingue classe et interface.

=== Étape 2 : ne garder que les types comptés
- *Quoi* : `CountingRules.isCountedType` décide si un type compte.
- *Pourquoi* : le § 4.1 exclut les classes anonymes, les classes locales et les types d'annotation. La règle est écrite une seule fois et partagée par tous les visiteurs, pour que la structure, les appels et les comptages s'accordent.
- *Comment* : on remonte les parents du nœud. Si le parent est l'unité de compilation, le type est de premier niveau : compté. Si le parent est un autre type, c'est un type membre : on continue à remonter. Tout autre parent arrête la remontée : `TypeDeclarationStatement` pour une classe locale, `AnonymousClassDeclaration` pour un membre de classe anonyme.

=== Étape 3 : nom, paquetage et héritage par les bindings
- *Quoi* : le nom qualifié vient de `ITypeBinding.getQualifiedName()`, le paquetage de `getPackage()`, les superclasses d'une boucle sur `getSuperclass()`, les interfaces directes de `getInterfaces()`.
- *Pourquoi* : le fichier analysé ne contient que du texte. `extends Exception` ne dit ni que `Exception` est `java.lang.Exception`, ni que sa superclasse est `Throwable`. Seul le binding, résolu par le compilateur, connaît la chaîne complète.
- *Comment* : la boucle part de la superclasse directe et s'arrête à `java.lang.Object`, qui n'est pas listée (§ 4.1). Pour un type membre, le nom qualifié utilise des points (`library.service.LoanService.Loan`). Un fichier sans déclaration `package` donne le paquetage `(défaut)`.
- *Repli* : si le binding est nul, on prend les noms tels qu'ils sont écrits dans le fichier, pour ne pas perdre le type.

=== Étape 4 : les membres lus dans `bodyDeclarations()`
- *Quoi* : les attributs et les méthodes sont lus dans la liste des membres directs du type, sans visite récursive.
- *Pourquoi* : une visite récursive attribuerait à `LoanService` les méthodes de sa classe membre `LoanService.Loan`. La liste directe règle aussi trois exclusions du § 4.1 : les constantes d'énumération sont des `EnumConstantDeclaration` rangées ailleurs, les composants d'un record ne sont pas des `FieldDeclaration`, et les membres implicites (constructeur par défaut, `values()`) n'ont aucun nœud dans l'AST.
- *Attributs* : une `FieldDeclaration` peut déclarer plusieurs variables. On crée un attribut par fragment, donc `int a, b;` compte pour deux. Le type déclaré est celui écrit dans le code.
- *Visibilité* : on lit les modificateurs. `public`, `protected` ou `private` s'il est écrit ; sinon `package`, ou `public implicite` pour un champ d'interface.
- *Méthodes* : nom, `parameters().size()` (un paramètre `String...` compte pour un) et `isConstructor()`.

=== Étape 5 : les comptages globaux
`MetricsVisitor` compte les types par genre, les méthodes, les constructeurs et les attributs, avec les mêmes règles de `CountingRules`. `ProjectStats` met le résultat sous la forme de la phrase du point de contrôle.

== 3 - Résultats sur le projet de validation
#table(
  columns: (2fr, 2fr, 2fr),
  stroke: 0.5pt + luma(150),
  table.header([*Mesure*], [*Attendu*], [*Obtenu*]),
  [Types], [15 : 12 classes, 2 interfaces, 1 énumération], [identique],
  [Méthodes], [59, dont 9 constructeurs], [identique],
  [Attributs], [21], [21],
  [`LoanService`], [6 méthodes, 5 attributs], [identique],
  [`LoanService.Loan`], [4 méthodes, 3 attributs], [identique],
  [Superclasses de `LoanException`], [`java.lang.Exception`, `java.lang.Throwable`], [identique],
  [Attributs de `Item`], [`MAX_LOAN_DAYS` public, `id` et `title` protected, `borrower` private, `category` package], [identique],
)

== 4 - Validation
- `ControlPointA2Test` vérifie chaque ligne du point de contrôle.
- `ConventionsTest` vérifie les cas absents du projet de validation, sur un petit projet écrit pour cela : record, classe locale, classe anonyme, type d'annotation, `int a, b;`, champ d'interface, fichier sans paquetage.

== 5 - Limites
- Le type d'un attribut est le texte écrit dans le code (`List<Item>`), pas un nom qualifié.
- Les superclasses et interfaces génériques gardent leurs arguments de type (`java.lang.Enum<library.model.Category>` pour une énumération).
- Le constructeur compact d'un record est compté comme une méthode. La structure lui donne zéro paramètre (aucun n'est écrit), mais le modèle de faits lui donne ceux du record, car il les lit dans la clé de la méthode.


= Partie A3 : Extraire les appels et le type statique du receveur

== 1 - Ce que demande le sujet
Pour chaque méthode de chaque classe, la liste des méthodes appelées (§ 4.4) avec, pour chacune : son nom, le type statique du receveur, la méthode cible résolue ou la mention « non résolue », et la ligne de l'appel.

== 2 - Étapes de réalisation

=== Étape 1 : savoir dans quelle méthode et dans quel type on se trouve
- *Quoi* : `CallExtractorVisitor` tient deux piles, celle de la méthode courante et celle du type englobant.
- *Pourquoi une pile* : les types peuvent être imbriqués. Quand on sort de `LoanService.Loan`, il faut retrouver `LoanService` comme type englobant.
- *Comment* : on empile dans `visit(...)` et on dépile dans `endVisit(...)`, appelée quand tout le sous-arbre a été parcouru. Seuls les types et les méthodes comptés sont empilés.
- *Conséquence pour les lambdas et les classes anonymes* : leur méthode n'est pas empilée, donc le sommet de la pile reste la méthode comptée qui les contient. Leurs appels lui sont attribués, comme le demande le § 4.4.
- *Conséquence pour les initialiseurs de champ* : la pile des méthodes est vide, l'appel est ignoré.

=== Étape 2 : les nœuds qui sont des appels
- *Quoi* : `visit(MethodInvocation)` pour `a.m()`, `m()` et `Type.m()` ; `visit(SuperMethodInvocation)` pour `super.m()`.
- *Pourquoi ces deux-là seulement* : c'est la définition du § 4.4. Une référence de méthode (`Type::m`) est un autre nœud, que le visiteur ne traite pas : elle n'est donc pas comptée. Les appels de constructeurs sont rangés à part (voir B3).

=== Étape 3 : la cible résolue
- *Quoi* : `resolveMethodBinding()` donne la méthode choisie par le compilateur d'après le type statique du receveur.
- *Comment* : `getMethodDeclaration()` remonte de `List<Item>.add(Item)` à la déclaration générique `List.add(E)`. `MethodSignatureUtil.getMethodKey` en fait la clé `Type#nom(paramètres)`, avec les types effacés : `java.util.List#add(java.lang.Object)`.
- *Méthode héritée* : la clé part de `getDeclaringClass()`. `zadig.getTitle()` vise donc `library.model.Item#getTitle()`, la classe qui déclare la méthode, et non `Book`.
- *Binding nul* : la cible vaut `non résolue`. L'appel est gardé et compté, l'analyse continue.

=== Étape 4 : le type statique du receveur
Trois cas, dans cet ordre :
- *Receveur écrit* (`loanable.checkOut(member)`) : type de l'expression, par `getExpression().resolveTypeBinding()`. C'est le type déclaré de la variable, `Loanable`, et non le type de l'objet à l'exécution.
- *Pas de receveur* (`m()`) : si la méthode est statique (`Modifier.isStatic`), le type qui la déclare ; sinon le type englobant, sommet de la pile, car le receveur est `this`.
- *`super.m()`* : la superclasse directe du type englobant.
Dans tous les cas le type est désigné par son effacement (`java.util.List`). Si le type ne peut pas être résolu, un texte de repli est utilisé (`<type non résolu>`).

=== Étape 5 : la ligne et le classement de l'appel
- *Ligne* : `CompilationUnit.getLineNumber(node.getStartPosition())`.
- *Interne ou externe* : un appel résolu est interne si sa cible est la clé d'une méthode du projet, externe sinon (JDK, bibliothèque).

== 3 - Résultats sur le projet de validation
#table(
  columns: (3fr, 2fr, 1fr),
  stroke: 0.5pt + luma(150),
  table.header([*Mesure*], [*Attendu*], [*Obtenu*]),
  [Appels], [94 (93 `MethodInvocation`, 1 `SuperMethodInvocation`)], [94],
  [Vers le projet], [46], [46],
  [Vers l'extérieur], [48], [48],
  [Non résolus], [0], [0],
  [`LoanService.borrow`, ligne 36, `loanable.checkOut(member)`], [receveur `library.model.Loanable`], [identique],
  [`LibraryApp.main`, ligne 32, `zadig.getTitle()`], [receveur `library.model.Book`, cible `library.model.Item#getTitle()`], [identique],
  [`Book.describe`, ligne 32, `super.describe()`], [receveur `library.model.Item`], [identique],
  [`Catalog.available`, ligne 37, lambda, `item.isAvailable()`], [receveur `library.model.Item`], [identique],
)

Sur `robustness/missing-dep` : `StringUtils.capitalize` est signalé non résolu, `formatter.decorate` est résolu vers `demo.Formatter#decorate(java.lang.String)`, et l'analyse va jusqu'au bout.

== 4 - Validation
- `ControlPointA3Test` vérifie les comptages, les quatre cas de receveur et l'échantillon `missing-dep`.
- `ConventionsTest` vérifie l'attribution des appels écrits dans une classe locale, une classe anonyme et une lambda, et le receveur implicite dans une énumération.

== 5 - Limites
- Les bibliothèques du projet analysé ne sont pas fournies au parseur : leurs appels restent non résolus.
- Dans une classe anonyme ou locale, un appel sans receveur est rattaché au type compté englobant, alors que le `this` réel est la classe anonyme.
- Le receveur est un type statique : il ne dit pas quelle redéfinition sera exécutée.


= Partie B1 : Un modèle de faits indépendant de JDT

== 1 - Ce que demande le sujet
Représenter les faits par nos propres classes, construites en une fois à partir de toutes les unités de compilation ; faire en sorte que B2 et B3 ne manipulent que ce modèle, sans aucune classe de `org.eclipse.jdt` ; justifier les choix de représentation.

== 2 - Étapes de réalisation

=== Étape 1 : les classes du modèle
- *Quoi* : le paquetage `model.facts` contient quatre records et un conteneur.
#table(
  columns: (1fr, 3fr),
  stroke: 0.5pt + luma(150),
  table.header([*Classe*], [*Contenu*]),
  [`TypeFact`], [nom qualifié, genre, paquetage, superclasses, interfaces, attributs, clés de ses méthodes],
  [`FieldFact`], [nom, type déclaré, visibilité],
  [`MethodFact`], [clé, nom simple, type déclarant, constructeur ou non, nombre de paramètres, lignes du corps, présence d'un corps, appels, appels de constructeurs],
  [`CallFact`], [ligne, nom de la méthode appelée, type statique du receveur, cible résolue],
  [`ProjectFacts`], [tous les types, toutes les méthodes, les paquetages, le nombre de lignes de l'application],
)
- *Pourquoi des records* : un fait ne change plus une fois extrait. Un record est immuable, et les listes qu'il reçoit sont rendues non modifiables dans son constructeur. Aucune étape de la partie B ne peut donc altérer les faits d'une autre.
- *Pourquoi seulement des chaînes, des entiers et des booléens* : un `ITypeBinding` ou un nœud d'AST gardé dans le modèle ferait dépendre B2 et B3 de JDT. Tout ce qui vient de JDT est converti en texte au moment de l'extraction.

=== Étape 2 : deux niveaux, `model.infos` puis `model.facts`
- *Quoi* : les visiteurs remplissent des objets simples et modifiables (`ClassInfo`, `CallSiteInfo`). `FactBuilder` les convertit ensuite en faits.
- *Pourquoi* : un visiteur découvre les informations au fil du parcours, il lui faut des objets qu'il peut compléter. Le modèle de faits, lui, doit être complet, trié et figé. Séparer les deux évite d'avoir un modèle à moitié rempli.

=== Étape 3 : `FactBuilder` construit tout en une fois
`FactBuilder.build` reçoit tous les fichiers analysés et les deux visiteurs, puis procède en trois temps :
- *Mesures* : pour chaque fichier, le nombre de lignes et le paquetage déclaré ; pour chaque méthode, les lignes de son corps.
- *Méthodes* : un `MethodFact` par méthode comptée, avec ses appels triés. Le nom simple, le type déclarant et le nombre de paramètres sont lus dans la clé de la méthode.
- *Types* : un `TypeFact` par type, avec ses attributs triés et les clés des méthodes dont la clé commence par son nom qualifié suivi de `#`.
`FactBuilder` est la dernière classe à voir JDT : c'est la frontière entre l'extraction (partie A) et l'exploitation (partie B).

=== Étape 4 : résoudre les appels entre fichiers
- *Quoi* : un appel de `LoanService` vers `Catalog`, déclaré dans un autre fichier, doit avoir une cible résolue.
- *Comment* : `JdtParser` analyse tous les fichiers en un seul lot (`createASTs`) et donne la racine des sources à `setEnvironment`. Les bindings sont alors partagés entre les fichiers.
- *Pourquoi cela suffit dans le modèle* : la cible d'un appel et la clé de la méthode déclarée sont calculées par la même fonction, `MethodSignatureUtil.getMethodKey`. Ce sont donc deux chaînes identiques, et savoir si un appel est interne revient à chercher sa cible dans la table des méthodes (`ProjectFacts.isInternalMethod`).
- *Exemple* : dans `LoanService#borrow`, ligne 33, `catalog.findById(itemId)` a pour cible `library.service.Catalog#findById(java.lang.String)`, qui est une clé de la table.

=== Étape 5 : le déterminisme
- Les fichiers sont triés avant l'analyse.
- Les types et les méthodes sont rangés dans des `TreeMap`, donc parcourus dans l'ordre de leur clé.
- Les attributs et les appels d'une méthode sont triés ; les faits implémentent `Comparable`.
Deux exécutions donnent ainsi les mêmes collections dans le même ordre, et donc la même sortie.

== 3 - Justification des choix de représentation

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
    [Identifiées par leur nom qualifié écrit avec des points (`library.service.LoanService.Loan`), comme l'impose le § 4.1. Elles disposent de leur propre `TypeFact`, permettant un traitement uniforme avec les types de premier niveau.],

    [Appels non résolus],
    [Représentés par la cible sentinelle `"non résolue"`. Cela prévient tout `NullPointerException` dans les étapes suivantes tout en permettant un filtrage direct via `CallFact.isResolved()`.],

    [Déterminisme absolu],
    [Emploi exclusif de `TreeMap` et de listes triées implémentant `Comparable` pour garantir une reproductibilité stricte entre deux exécutions consécutives.]
  ),
  caption: [Justification des choix de représentation pour le modèle de faits (B1)]
)

== 4 - Résultats sur le projet de validation
Les chiffres de A2 et A3 sont retrouvés en lisant seulement le modèle (action `faits`) :
#table(
  columns: (2fr, 2fr, 1fr),
  stroke: 0.5pt + luma(150),
  table.header([*Mesure*], [*Attendu*], [*Obtenu*]),
  [Types], [15 : 12 classes, 2 interfaces, 1 énumération], [identique],
  [Méthodes], [59, dont 9 constructeurs], [identique],
  [Attributs], [21], [21],
  [Appels], [94 : 46 internes, 48 externes, 0 non résolu], [identique],
  [Appel de `LoanService` vers `Catalog`], [résolu], [résolu],
  [Deux exécutions successives], [sortie identique], [identique],
)

== 5 - Validation
- `ControlPointB1Test` recalcule les chiffres de A2 et A3 à partir de `ProjectFacts`, vérifie qu'un appel de `LoanService` vers `Catalog` est résolu, et compare deux constructions successives (mêmes types, mêmes méthodes, mêmes appels, dans le même ordre).
- Indépendance vis-à-vis de JDT : aucun fichier des paquetages `model.facts`, `model.infos`, `analysis`, `report` et `cli` n'importe `org.eclipse.jdt`. Seuls `parse`, `visitors`, `utils` et `Analysis` le font.

== 6 - Limites
- Le nombre de paramètres d'un `MethodFact` est relu dans la clé, en comptant les virgules. Cela fonctionne parce que les types y sont effacés : `Map<String, Item>` devient `java.util.Map`, sans virgule.
- Si le binding d'une déclaration de méthode était nul, sa clé serait `non résolue` : deux méthodes dans ce cas se confondraient. Cela ne s'est produit sur aucun des projets analysés, y compris les échantillons de robustesse.
- Le type d'un attribut est le texte écrit dans le code (`List<Item>`), pas un nom qualifié : le champ de `FieldFact` s'appelle `declaredType`.


= Partie B2 : Métriques

== 1 - Ce que demande le sujet
Calculer 13 métriques avec les conventions du § 4 : comptages, moyennes, classements « 10 % », seuil X et nombre maximal de paramètres.

== 2 - Étapes de réalisation

=== Étape 1 : une classe qui ne lit que le modèle de faits
- *Quoi* : `MetricsService`, dans le paquetage `analysis`, reçoit un `ProjectFacts` et offre une méthode par question.
- *Pourquoi* : la partie B ne relit plus le code. `MetricsService` n'importe aucune classe JDT, et chaque métrique peut être testée sur des faits construits à la main.

=== Étape 2 : les données mesurées à l'extraction
Trois valeurs ne peuvent pas se déduire des faits : elles sont mesurées par `FactBuilder` puis rangées dans le modèle.
- *Lignes de l'application* : `Files.readAllLines(chemin).size()` pour chaque fichier, lignes vides et commentaires compris (§ 4.2).
- *Lignes d'une méthode* : ligne de l'accolade fermante moins ligne de l'accolade ouvrante, plus un. Les positions viennent du corps de la méthode (`getBody()`), donc la signature, la Javadoc et les annotations ne comptent pas. Une méthode sans corps vaut 0.
- *Paquetages* : le nom déclaré par chaque fichier, ou `(défaut)` sans déclaration. Relevés sur les fichiers et non sur les types, un fichier sans type compté déclare quand même son paquetage.

=== Étape 3 : comptages et moyennes (questions 1 à 7)
- Les questions 1 à 4 sont des tailles de collections du modèle.
- Les moyennes sont des divisions réelles, avec 0 si le dénominateur est nul.
- La question 6 ne porte que sur les méthodes ayant un corps : 246 lignes pour 53 méthodes.
- L'affichage utilise deux décimales (`%.2f`) et la virgule française.

=== Étape 4 : une seule fonction pour « les 10 % » (questions 8, 9 et 12)
- *Quoi* : `getTop10Percent(candidats, mesure)`, utilisée par les trois questions.
- *Pourquoi une seule* : la règle du § 4.3 est la même partout. L'écrire une fois évite qu'une des trois questions l'applique différemment.
- *Comment*, dans l'ordre :
  - `k = (n + 9) / 10`, qui calcule le plafond de n/10 en entiers. Un transtypage `(int) (n * 0.1)` arrondirait vers le bas et donnerait 1 au lieu de 2 pour n = 15.
  - tri décroissant sur la valeur mesurée. Le tri est stable : à valeur égale, l'ordre alphabétique des candidats est gardé, ce qui rend la sortie déterministe.
  - le seuil est la valeur du k-ième.
  - on retient tous les candidats dont la valeur atteint le seuil : les ex-æquo du k-ième sont gardés, le résultat peut dépasser k.
  - un candidat dont la valeur vaut 0 n'est jamais retenu.
- *Exemple (question 8)* : n = 15, k = 2. Le 2#super[e] vaut 6, donc `Item` (10), `Member` (6) et `LoanService` (6) sont retenues : 3 classes pour k = 2.
- *Question 12* : le calcul est fait classe par classe. Les candidats sont toutes les méthodes de la classe, corps ou non ; n et k portent donc sur toutes. Pour `Loanable`, toutes les valeurs sont à 0 : aucune méthode retenue.

=== Étape 5 : intersection, seuil X et paramètres
- *Question 10* : les classes de la question 9 dont le nom figure dans le résultat de la question 8.
- *Question 11* : classes ayant strictement plus de X méthodes (`> x`). X vient de l'option `--x` ; une valeur absente, non entière ou négative est refusée avant l'analyse.
- *Question 13* : le maximum du nombre de paramètres, puis toutes les méthodes qui l'atteignent.

== 3 - Résultats sur le projet de validation
#table(
  columns: (0.5fr, 3fr, 3fr),
  stroke: 0.5pt + luma(150),
  table.header([*Q.*], [*Métrique*], [*Obtenu (égal à l'attendu)*]),
  [1], [Nombre de classes], [15],
  [2], [Lignes de code de l'application], [493],
  [3], [Nombre de méthodes], [59],
  [4], [Nombre de paquetages], [4],
  [5], [Méthodes par classe], [3,93],
  [6], [Lignes par méthode ayant un corps], [4,64 (246 lignes, 53 méthodes)],
  [7], [Attributs par classe], [1,40],
  [8], [10 % des classes, par méthodes], [`Item` (10), `Member` (6), `LoanService` (6)],
  [9], [10 % des classes, par attributs], [`Item` (5), `LoanService` (5)],
  [10], [Intersection de 8 et 9], [`Item`, `LoanService`],
  [11], [Plus de X méthodes], [X = 5 : 3 classes ; X = 4 : 6 classes],
  [12], [10 % des méthodes les plus longues, par classe], [`Item` : `checkOut` (7) ; `LoanService` : `borrow` (14) ; `Member` : ses 6 méthodes (3) ; `Loanable` : aucune],
  [13], [Maximum de paramètres], [5 : `Statistics#report`],
)

== 4 - Validation
- `ControlPointB2Test` vérifie les 13 valeurs.
- `ConventionsTest` vérifie deux cas absents du projet de validation, sur des faits construits à la main : un classement dont toutes les valeurs sont à 0 ne retient personne ; pour une classe de 11 méthodes dont 2 sans corps, k vaut 2.

== 5 - Regard critique
- Les lignes sont des lignes physiques : un fichier très commenté paraît plus gros qu'il ne l'est.
- Une moyenne cache la dispersion : 3,93 méthodes par classe mélange `Item` (10) et des classes à une méthode.
- Le nombre de méthodes ou d'attributs mesure une taille, pas une qualité : une grande classe n'est pas forcément mal conçue.


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
