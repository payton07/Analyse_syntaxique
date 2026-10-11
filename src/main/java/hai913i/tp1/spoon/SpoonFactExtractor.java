package hai913i.tp1.spoon;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.TreeMap;
import java.util.TreeSet;

import hai913i.tp1.model.facts.CallFact;
import hai913i.tp1.model.facts.FieldFact;
import hai913i.tp1.model.facts.MethodFact;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.model.facts.TypeFact;
import hai913i.tp1.parse.ProjectSources;
import hai913i.tp1.visitors.CountingRules;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtBlock;
import spoon.reflect.code.CtConstructorCall;
import spoon.reflect.code.CtExpression;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.code.CtSuperAccess;
import spoon.reflect.code.CtTypeAccess;
import spoon.reflect.cu.SourcePosition;
import spoon.reflect.declaration.CtAnnotationType;
import spoon.reflect.declaration.CtClass;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtElement;
import spoon.reflect.declaration.CtEnum;
import spoon.reflect.declaration.CtEnumValue;
import spoon.reflect.declaration.CtExecutable;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtInterface;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtPackage;
import spoon.reflect.declaration.CtParameter;
import spoon.reflect.declaration.CtRecord;
import spoon.reflect.declaration.CtType;
import spoon.reflect.declaration.CtTypeMember;
import spoon.reflect.declaration.CtTypeParameter;
import spoon.reflect.declaration.ModifierKind;
import spoon.reflect.reference.CtArrayTypeReference;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.reference.CtTypeReference;
import spoon.reflect.reference.CtWildcardReference;
import spoon.reflect.visitor.filter.TypeFilter;
import spoon.support.reflect.CtExtendedModifier;

/**
 * Seconde extraction, avec Spoon (partie C1) : produit le même ProjectFacts que la chaîne JDT.
 *
 * Spoon ne compte pas comme le sujet (constructeurs implicites, constantes d'énumération rangées
 * parmi les attributs, appels implicites à super(), noms imbriqués en $). Cette classe applique
 * donc les conventions du § 4 avant de remplir le modèle de faits. Les clés de méthode ont le
 * format de MethodSignatureUtil : les deux modèles sont comparables entrée par entrée.
 */
public final class SpoonFactExtractor {

    private static final String UNRESOLVED = "non résolue";
    private static final String UNRESOLVED_TYPE = "<type non résolu>";

    private SpoonFactExtractor() {
    }

    public static ProjectFacts extract(ProjectSources sources) throws IOException {
        return extract(SpoonModel.build(sources), sources);
    }

    static ProjectFacts extract(CtModel model, ProjectSources sources) throws IOException {
        Map<String, TypeFact> typeFacts = new TreeMap<>();
        Map<String, MethodFact> methodFacts = new TreeMap<>();

        for (CtType<?> type : model.getElements(new TypeFilter<>(CtType.class))) {
            if (!isCountedType(type)) continue;

            List<String> methodKeys = new ArrayList<>();
            for (CtExecutable<?> executable : countedExecutables(type)) {
                MethodFact fact = toMethodFact(executable, type);
                methodFacts.put(fact.methodKey(), fact);
                methodKeys.add(fact.methodKey());
            }
            Collections.sort(methodKeys);

            String name = typeName(type);
            typeFacts.put(name, new TypeFact(name, kindOf(type), packageOf(type),
                    superClassesOf(type), interfacesOf(type), fieldsOf(type), methodKeys));
        }

        return new ProjectFacts(typeFacts, methodFacts, packagesOf(model), totalLines(sources));
    }

    // ------------------------------------------------------------------------
    // Règles de comptage du § 4.1, sur le métamodèle de Spoon
    // ------------------------------------------------------------------------

    // Type compté : classe, interface, énumération ou record nommé, de premier niveau ou membre.
    // CtTypeParameter (le T d'une classe générique) est un CtType pour Spoon : exclu.
    static boolean isCountedType(CtType<?> type) {
        if (type instanceof CtTypeParameter || type instanceof CtAnnotationType) return false;
        if (type.isImplicit() || type.isAnonymous() || type.isLocalType()) return false;
        CtType<?> outer = type.getDeclaringType();
        return outer == null || isCountedType(outer);
    }

    // Méthodes et constructeurs écrits dans le type : Spoon ajoute un constructeur par défaut
    // implicite aux classes qui n'en déclarent pas, que le sujet ne compte pas.
    static List<CtExecutable<?>> countedExecutables(CtType<?> type) {
        List<CtExecutable<?>> executables = new ArrayList<>();
        for (CtMethod<?> method : type.getMethods()) {
            if (!method.isImplicit()) executables.add(method);
        }
        if (type instanceof CtClass<?> ctClass) {
            for (CtConstructor<?> constructor : ctClass.getConstructors()) {
                if (!constructor.isImplicit()) executables.add(constructor);
            }
        }
        return executables;
    }

    // ------------------------------------------------------------------------
    // Types
    // ------------------------------------------------------------------------

    private static String kindOf(CtType<?> type) {
        // CtEnum et CtRecord sont des CtClass : testés d'abord
        if (type instanceof CtInterface) return "Interface";
        if (type instanceof CtEnum) return "Énumération";
        if (type instanceof CtRecord) return "Record";
        return "Classe";
    }

    private static String packageOf(CtType<?> type) {
        CtPackage pkg = type.getTopLevelType().getPackage();
        return (pkg == null || pkg.isUnnamedPackage()) ? CountingRules.DEFAULT_PACKAGE : pkg.getQualifiedName();
    }

    // Toutes les superclasses jusqu'à Object exclu, comme la chaîne JDT
    private static List<String> superClassesOf(CtType<?> type) {
        List<String> names = new ArrayList<>();
        if (type instanceof CtEnum) {
            // Superclasse implicite d'une énumération E : Enum<E>. Spoon la donne sans son argument.
            names.add("java.lang.Enum<" + typeName(type) + ">");
            return names;
        }
        CtTypeReference<?> current = type.getSuperclass();
        while (current != null && !"java.lang.Object".equals(current.getQualifiedName())) {
            names.add(writtenName(current, true));
            current = current.getSuperclass();
        }
        return names;
    }

    private static List<String> interfacesOf(CtType<?> type) {
        List<String> names = new ArrayList<>();
        for (CtTypeReference<?> itf : type.getSuperInterfaces()) {
            names.add(writtenName(itf, true));
        }
        return names;
    }

    private static List<FieldFact> fieldsOf(CtType<?> type) {
        List<FieldFact> fields = new ArrayList<>();
        for (CtField<?> field : type.getFields()) {
            // Spoon range les constantes d'énumération parmi les attributs (CtEnumValue) : § 4.1, non comptées
            if (field instanceof CtEnumValue || field.isImplicit()) continue;
            fields.add(new FieldFact(field.getSimpleName(), writtenName(field.getType(), false),
                    visibilityOf(field, type instanceof CtInterface)));
        }
        Collections.sort(fields);
        return fields;
    }

    // Seuls les modificateurs écrits comptent : Spoon ajoute « public » aux membres d'une interface
    private static String visibilityOf(CtTypeMember member, boolean inInterface) {
        for (CtExtendedModifier modifier : member.getExtendedModifiers()) {
            if (modifier.isImplicit()) continue;
            if (modifier.getKind() == ModifierKind.PUBLIC) return "public";
            if (modifier.getKind() == ModifierKind.PROTECTED) return "protected";
            if (modifier.getKind() == ModifierKind.PRIVATE) return "private";
        }
        return inInterface ? "public implicite" : "package";
    }

    // Paquetages qui contiennent au moins un type. getAllPackages() renvoie aussi le paquetage
    // racine et les paquetages intermédiaires (library), qu'aucun fichier ne déclare.
    private static Set<String> packagesOf(CtModel model) {
        Set<String> packages = new TreeSet<>();
        for (CtPackage pkg : model.getAllPackages()) {
            if (pkg.getTypes().isEmpty()) continue;
            packages.add(pkg.isUnnamedPackage() ? CountingRules.DEFAULT_PACKAGE : pkg.getQualifiedName());
        }
        return packages;
    }

    // Lignes physiques des fichiers (§ 4.2) : une mesure sur les fichiers, indépendante de l'analyseur
    private static int totalLines(ProjectSources sources) throws IOException {
        int total = 0;
        for (Path file : sources.javaFiles()) {
            total += Files.readAllLines(file).size();
        }
        return total;
    }

    // ------------------------------------------------------------------------
    // Méthodes et appels
    // ------------------------------------------------------------------------

    private static MethodFact toMethodFact(CtExecutable<?> executable, CtType<?> declaringType) {
        boolean isConstructor = executable instanceof CtConstructor;
        CtBlock<?> body = executable.getBody();
        int lines = 0;
        if (body != null && body.getPosition().isValidPosition()) {
            lines = body.getPosition().getEndLine() - body.getPosition().getLine() + 1;
        }

        List<CallFact> calls = new ArrayList<>();
        List<CallFact> constructorCalls = new ArrayList<>();
        if (body != null) {
            collectCalls(body, declaringType, calls, constructorCalls);
        }
        Collections.sort(calls);
        Collections.sort(constructorCalls);

        return new MethodFact(methodKey(executable), isConstructor ? "<init>" : executable.getSimpleName(),
                typeName(declaringType), isConstructor, executable.getParameters().size(),
                lines, body != null, calls, constructorCalls);
    }

    // Les appels écrits dans une classe anonyme, une classe locale ou une lambda du corps sont
    // attribués à cette méthode (§ 4.4) : getElements descend dans tout le corps.
    private static void collectCalls(CtBlock<?> body, CtType<?> enclosingType,
                                     List<CallFact> calls, List<CallFact> constructorCalls) {
        for (CtInvocation<?> invocation : body.getElements(new TypeFilter<>(CtInvocation.class))) {
            // Spoon ajoute un appel super() implicite en tête de chaque constructeur
            if (invocation.isImplicit()) continue;

            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable.isConstructor()) {
                // this(...) et super(...) : hors du § 4.4, gardés pour l'option du graphe
                String receiver = isSuperConstructorCall(invocation, enclosingType)
                        ? superclassName(enclosingType) : typeName(enclosingType);
                constructorCalls.add(new CallFact(lineOf(invocation), "<init>", receiver, targetKey(executable)));
            } else {
                calls.add(new CallFact(lineOf(invocation), executable.getSimpleName(),
                        receiverOf(invocation, enclosingType), targetKey(executable)));
            }
        }
        for (CtConstructorCall<?> creation : body.getElements(new TypeFilter<>(CtConstructorCall.class))) {
            // new T(...) : le « receveur » est le type instancié
            constructorCalls.add(new CallFact(lineOf(creation), "<init>",
                    erasedName(creation.getType(), UNRESOLVED_TYPE), targetKey(creation.getExecutable())));
        }
    }

    // super(...) vise un constructeur d'un autre type que le type englobant ; this(...) vise le même
    private static boolean isSuperConstructorCall(CtInvocation<?> invocation, CtType<?> enclosingType) {
        CtTypeReference<?> declaring = invocation.getExecutable().getDeclaringType();
        return declaring != null && !declaring.getQualifiedName().equals(enclosingType.getQualifiedName());
    }

    // Type statique du receveur (§ 4.4), avec les mêmes cas que CallExtractorVisitor
    private static String receiverOf(CtInvocation<?> invocation, CtType<?> enclosingType) {
        CtExpression<?> target = invocation.getTarget();
        if (target instanceof CtSuperAccess) {
            return superclassName(enclosingType);
        }
        if (target == null || target.isImplicit()) {
            // m() sans receveur écrit : le type déclarant si m est statique, sinon le type englobant
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable.isStatic() && executable.getDeclaringType() != null) {
                return erasedName(executable.getDeclaringType(), "<inconnu>");
            }
            return typeName(enclosingType);
        }
        CtTypeReference<?> type = target instanceof CtTypeAccess<?> typeAccess
                ? typeAccess.getAccessedType() : target.getType();
        return isVerified(type) ? erasedName(type, UNRESOLVED_TYPE) : UNRESOLVED_TYPE;
    }

    private static String superclassName(CtType<?> type) {
        CtTypeReference<?> superclass = type.getSuperclass();
        return superclass != null ? erasedName(superclass, "super") : "java.lang.Object";
    }

    // En mode noClasspath, Spoon devine la cible d'un appel vers une bibliothèque absente, d'après
    // l'import. Une cible n'est donc dite résolue que si le type qui la déclare est vérifié.
    private static String targetKey(CtExecutableReference<?> reference) {
        if (!isVerified(reference.getDeclaringType())) return UNRESOLVED;
        CtExecutable<?> declaration = reference.getExecutableDeclaration();
        return declaration != null ? methodKey(declaration) : UNRESOLVED;
    }

    // Type vérifié : déclaré dans les sources analysées, ou fourni par le JDK.
    // Spoon retrouve aussi par réflexion les classes de son propre classpath (commons-lang3 est une
    // de ses dépendances) : elles appartiennent à l'analyseur, pas au projet analysé, et sont refusées.
    static boolean isVerified(CtTypeReference<?> reference) {
        if (reference == null) return false;
        if (reference instanceof CtArrayTypeReference<?> array) return isVerified(array.getComponentType());
        if (reference.isPrimitive()) return true;
        CtType<?> declaration = reference.getTypeDeclaration();
        if (declaration == null) return false;
        if (!declaration.isShadow()) return true;
        try {
            ClassLoader loader = reference.getActualClass().getClassLoader();
            return loader == null || loader == ClassLoader.getPlatformClassLoader();
        } catch (RuntimeException e) {
            return false;
        }
    }

    private static int lineOf(CtElement element) {
        SourcePosition position = element.getPosition();
        return position.isValidPosition() ? position.getLine() : 0;
    }

    // ------------------------------------------------------------------------
    // Noms : mêmes formats que la chaîne JDT
    // ------------------------------------------------------------------------

    /** Clé pkg.Type#nom(ParamEffacé1,ParamEffacé2), <init> pour un constructeur. */
    static String methodKey(CtExecutable<?> executable) {
        CtType<?> declaringType = executable instanceof CtTypeMember member
                ? member.getDeclaringType() : executable.getParent(CtType.class);
        String name = executable instanceof CtConstructor ? "<init>" : executable.getSimpleName();

        StringJoiner params = new StringJoiner(",");
        for (CtParameter<?> parameter : executable.getParameters()) {
            params.add(erasedName(parameter.getType(), "<inconnu>"));
        }
        return (declaringType != null ? typeName(declaringType) : "<inconnu>") + "#" + name + "(" + params + ")";
    }

    // Spoon sépare un type imbriqué par $ (LoanService$Loan) ; le sujet et JDT l'écrivent avec un point.
    // Une classe locale ou anonyme n'a pas de nom qualifié : chaîne vide, comme le binding de JDT.
    static String typeName(CtType<?> type) {
        if (type.isAnonymous() || type.isLocalType()) return "";
        CtType<?> outer = type.getDeclaringType();
        return outer != null ? typeName(outer) + "." + type.getSimpleName() : type.getQualifiedName();
    }

    // Nom qualifié après effacement des génériques : List<Item> donne java.util.List, T donne sa borne
    private static String erasedName(CtTypeReference<?> reference, String fallback) {
        if (reference == null) return fallback;
        CtTypeReference<?> erased = reference.getTypeErasure();
        if (erased instanceof CtArrayTypeReference<?> array) {
            return erasedName(array.getComponentType(), fallback) + "[]";
        }
        if (erased.isAnonymous() || erased.isLocalType()) return "";
        CtTypeReference<?> outer = erased.getDeclaringType();
        return outer != null ? erasedName(outer, fallback) + "." + erased.getSimpleName()
                : erased.getQualifiedName();
    }

    // Type tel qu'il est déclaré, arguments génériques compris : List<Item> en noms simples pour un
    // attribut, java.lang.Comparable<library.model.Item> en noms qualifiés pour un supertype.
    private static String writtenName(CtTypeReference<?> reference, boolean qualified) {
        if (reference == null) return UNRESOLVED_TYPE;
        if (reference instanceof CtArrayTypeReference<?> array) {
            return writtenName(array.getComponentType(), qualified) + "[]";
        }
        if (reference instanceof CtWildcardReference wildcard) {
            CtTypeReference<?> bound = wildcard.getBoundingType();
            if (wildcard.isDefaultBoundingType() || bound == null) return "?";
            return (wildcard.isUpper() ? "? extends " : "? super ") + writtenName(bound, qualified);
        }
        String name = qualified ? erasedName(reference, UNRESOLVED_TYPE) : reference.getSimpleName();
        List<CtTypeReference<?>> arguments = reference.getActualTypeArguments();
        if (arguments.isEmpty()) return name;

        StringJoiner joined = new StringJoiner(",", "<", ">");
        for (CtTypeReference<?> argument : arguments) {
            joined.add(writtenName(argument, qualified));
        }
        return name + joined;
    }
}
