package hai913i.tp1.spoon;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.parse.ProjectSources;
import spoon.reflect.CtModel;
import spoon.reflect.code.CtInvocation;
import spoon.reflect.declaration.CtConstructor;
import spoon.reflect.declaration.CtEnumValue;
import spoon.reflect.declaration.CtField;
import spoon.reflect.declaration.CtMethod;
import spoon.reflect.declaration.CtType;
import spoon.reflect.reference.CtExecutableReference;
import spoon.reflect.visitor.filter.TypeFilter;

/**
 * Comparaison des deux extractions sur un projet (partie C1).
 *
 * @param raw            ce que Spoon compte sans aucune convention du sujet
 * @param spoonFacts     le modèle de faits tiré de Spoon, conventions du § 4 appliquées
 * @param differences    écarts entre ce modèle et celui de JDT (vide s'ils sont identiques)
 * @param guessedTargets cibles que Spoon propose pour un type qu'il n'a pas pu vérifier
 */
public record EngineComparison(RawCounts raw, ProjectFacts spoonFacts,
                               List<String> differences, List<String> guessedTargets) {

    /** Comptages bruts de Spoon : les requêtes les plus directes, sans filtre. */
    public record RawCounts(
            int topLevelTypes,          // model.getAllTypes()
            int allTypes,               // tous les CtType
            int methods,                // CtMethod
            int constructors,           // CtConstructor
            int implicitConstructors,   // dont ceux que Spoon ajoute
            int fields,                 // CtField
            int enumValues,             // dont les constantes d'énumération
            int packages,               // model.getAllPackages()
            int invocations,            // CtInvocation
            int constructorInvocations, // dont this(...) et super(...)
            int implicitInvocations     // dont les super() que Spoon ajoute
    ) {
    }

    public static EngineComparison of(ProjectSources sources, ProjectFacts jdtFacts) throws IOException {
        CtModel model = SpoonModel.build(sources);
        ProjectFacts spoonFacts = SpoonFactExtractor.extract(model, sources);
        return new EngineComparison(rawCounts(model), spoonFacts,
                FactComparison.differences(jdtFacts, spoonFacts), guessedTargets(model));
    }

    private static RawCounts rawCounts(CtModel model) {
        List<CtConstructor<?>> constructors = model.getElements(new TypeFilter<>(CtConstructor.class));
        List<CtField<?>> fields = model.getElements(new TypeFilter<>(CtField.class));
        List<CtInvocation<?>> invocations = model.getElements(new TypeFilter<>(CtInvocation.class));

        return new RawCounts(
                model.getAllTypes().size(),
                model.getElements(new TypeFilter<>(CtType.class)).size(),
                model.getElements(new TypeFilter<>(CtMethod.class)).size(),
                constructors.size(),
                (int) constructors.stream().filter(CtConstructor::isImplicit).count(),
                fields.size(),
                (int) fields.stream().filter(f -> f instanceof CtEnumValue).count(),
                model.getAllPackages().size(),
                invocations.size(),
                (int) invocations.stream().filter(i -> i.getExecutable().isConstructor()).count(),
                (int) invocations.stream().filter(CtInvocation::isImplicit).count());
    }

    // Mode noClasspath : Spoon nomme un type déclarant même quand il ne l'a pas trouvé
    private static List<String> guessedTargets(CtModel model) {
        List<String> guesses = new ArrayList<>();
        for (CtInvocation<?> invocation : model.getElements(new TypeFilter<>(CtInvocation.class))) {
            CtExecutableReference<?> executable = invocation.getExecutable();
            if (executable.getDeclaringType() != null && !SpoonFactExtractor.isVerified(executable.getDeclaringType())) {
                String where = invocation.getPosition().isValidPosition()
                        ? invocation.getPosition().getFile().getName() + ":" + invocation.getPosition().getLine() + " "
                        : "";
                guesses.add(where + executable.getDeclaringType().getQualifiedName() + "#" + executable.getSimpleName());
            }
        }
        guesses.sort(null);
        return guesses;
    }
}
