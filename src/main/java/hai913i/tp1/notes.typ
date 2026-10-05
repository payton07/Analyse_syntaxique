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