package hai913i.tp1.spoon;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import hai913i.tp1.model.facts.MethodFact;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.model.facts.TypeFact;

/**
 * Comparaison de deux modèles de faits, entrée par entrée (partie C1).
 *
 * Les deux extractions remplissent le même modèle : les comparer ne demande ni JDT ni Spoon.
 */
public final class FactComparison {

    private FactComparison() {
    }

    /**
     * @return une ligne par écart, dans un ordre fixe ; liste vide si les deux modèles sont identiques
     */
    public static List<String> differences(ProjectFacts jdt, ProjectFacts spoon) {
        List<String> diffs = new ArrayList<>();

        compare(diffs, "Paquetages", jdt.getPackages(), spoon.getPackages());
        compare(diffs, "Lignes de l'application", jdt.getTotalAppLines(), spoon.getTotalAppLines());

        onlyInOne(diffs, "Type", jdt.getTypes().keySet(), spoon.getTypes().keySet());
        for (TypeFact a : jdt.getTypes().values()) {
            TypeFact b = spoon.getTypes().get(a.qualifiedName());
            if (b == null) continue;
            String where = "Type " + a.qualifiedName() + ", ";
            compare(diffs, where + "sorte", a.kind(), b.kind());
            compare(diffs, where + "paquetage", a.packageName(), b.packageName());
            compare(diffs, where + "superclasses", a.superClasses(), b.superClasses());
            compare(diffs, where + "interfaces", a.interfaces(), b.interfaces());
            compare(diffs, where + "attributs", a.fields(), b.fields());
            compare(diffs, where + "méthodes", a.methodKeys(), b.methodKeys());
        }

        onlyInOne(diffs, "Méthode", jdt.getMethods().keySet(), spoon.getMethods().keySet());
        for (MethodFact a : jdt.getMethods().values()) {
            MethodFact b = spoon.getMethods().get(a.methodKey());
            if (b == null) continue;
            String where = "Méthode " + a.methodKey() + ", ";
            compare(diffs, where + "paramètres", a.parameterCount(), b.parameterCount());
            compare(diffs, where + "lignes du corps", a.linesOfCode(), b.linesOfCode());
            compare(diffs, where + "corps présent", a.hasBody(), b.hasBody());
            compare(diffs, where + "appels", a.calls(), b.calls());
            compare(diffs, where + "appels de constructeurs", a.constructorCalls(), b.constructorCalls());
        }
        return diffs;
    }

    private static void compare(List<String> diffs, String what, Object jdt, Object spoon) {
        if (!Objects.equals(jdt, spoon)) {
            diffs.add(what + " : JDT = " + jdt + " ; Spoon = " + spoon);
        }
    }

    private static void onlyInOne(List<String> diffs, String what, Set<String> jdt, Set<String> spoon) {
        Set<String> all = new TreeSet<>(jdt);
        all.addAll(spoon);
        for (String name : all) {
            if (!spoon.contains(name)) diffs.add(what + " vu par JDT seulement : " + name);
            if (!jdt.contains(name)) diffs.add(what + " vu par Spoon seulement : " + name);
        }
    }
}
