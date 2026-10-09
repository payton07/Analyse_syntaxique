package hai913i.tp1.analysis;

import hai913i.tp1.model.facts.MethodFact;
import hai913i.tp1.model.facts.ProjectFacts;
import hai913i.tp1.model.facts.TypeFact;

import java.util.*;
import java.util.function.ToIntFunction;
import java.util.stream.Collectors;

public class MetricsService {

    private final ProjectFacts facts;

    public MetricsService(ProjectFacts facts) {
        this.facts = facts;
    }

    // Q1 : Nombre de classes (au sens types du projet : classes, interfaces, enums)
    public int getNbClasses() {
        return facts.getTypes().size();
    }

    // Q2 : Nombre total de lignes de code de l'application
    public int getTotalAppLines() {
        return facts.getTotalAppLines();
    }

    // Q3 : Nombre total de méthodes
    public int getTotalMethods() {
        return facts.getMethods().size();
    }

    // Q4 : Nombre total de paquetages
    public int getTotalPackages() {
        return facts.getPackages().size();
    }

    // Q5 : Nombre moyen de méthodes par classe
    public double getAvgMethodsPerClass() {
        if (getNbClasses() == 0) return 0.0;
        return (double) getTotalMethods() / getNbClasses();
    }

    // Q6 : Nombre moyen de lignes par méthode (méthodes ayant un corps)
    public double getAvgLinesPerMethodWithBody() {
        List<MethodFact> methodsWithBody = facts.getMethods().values().stream()
                .filter(MethodFact::hasBody)
                .toList();
        if (methodsWithBody.isEmpty()) return 0.0;
        int totalLoc = methodsWithBody.stream().mapToInt(MethodFact::linesOfCode).sum();
        return (double) totalLoc / methodsWithBody.size();
    }

    public int getTotalLinesOfMethodsWithBody() {
        return facts.getMethods().values().stream()
                .filter(MethodFact::hasBody)
                .mapToInt(MethodFact::linesOfCode)
                .sum();
    }

    public int getNbMethodsWithBody() {
        return (int) facts.getMethods().values().stream()
                .filter(MethodFact::hasBody)
                .count();
    }

    // Q7 : Nombre moyen d'attributs par classe
    public double getAvgAttributesPerClass() {
        if (getNbClasses() == 0) return 0.0;
        int totalFields = facts.getTypes().values().stream()
                .mapToInt(TypeFact::getFieldCount)
                .sum();
        return (double) totalFields / getNbClasses();
    }

    // Q8 : Les 10 % de classes possédant le plus de méthodes (avec gestion ex-aequo)
    public List<TypeFact> getTop10PercentClassesByMethods() {
        return getTop10Percent(new ArrayList<>(facts.getTypes().values()), TypeFact::getMethodCount);
    }

    // Q9 : Les 10 % de classes possédant le plus d'attributs (avec gestion ex-aequo)
    public List<TypeFact> getTop10PercentClassesByAttributes() {
        return getTop10Percent(new ArrayList<>(facts.getTypes().values()), TypeFact::getFieldCount);
    }

    // Q10 : Intersection des deux catégories précédentes
    public List<TypeFact> getIntersectionTopClasses() {
        Set<String> topMethods = getTop10PercentClassesByMethods().stream()
                .map(TypeFact::qualifiedName)
                .collect(Collectors.toSet());

        return getTop10PercentClassesByAttributes().stream()
                .filter(t -> topMethods.contains(t.qualifiedName()))
                .sorted(Comparator.comparing(TypeFact::qualifiedName))
                .toList();
    }

    // Q11 : Classes avec plus de X méthodes
    public List<TypeFact> getClassesWithMoreThanXMethods(int x) {
        return facts.getTypes().values().stream()
                .filter(t -> t.getMethodCount() > x)
                .sorted(Comparator.comparing(TypeFact::getMethodCount).reversed()
                        .thenComparing(TypeFact::qualifiedName))
                .toList();
    }

    // Q12 : Pour chaque classe, les 10 % de ses méthodes les plus longues.
    // Les candidats sont toutes les méthodes de la classe (§ 4.3), y compris celles sans corps (0 ligne).
    public Map<String, List<MethodFact>> getTop10PercentMethodsByClass() {
        Map<String, List<MethodFact>> result = new LinkedHashMap<>();

        for (TypeFact type : facts.getTypes().values()) {
            List<MethodFact> classMethods = type.methodKeys().stream()
                    .map(k -> facts.getMethods().get(k))
                    .filter(Objects::nonNull)
                    .toList();

            result.put(type.qualifiedName(), getTop10Percent(classMethods, MethodFact::linesOfCode));
        }

        return result;
    }

    // Q13 : Nombre maximal de paramètres et méthodes concernées
    public int getMaxParameters() {
        return facts.getMethods().values().stream()
                .mapToInt(MethodFact::parameterCount)
                .max()
                .orElse(0);
    }

    public List<MethodFact> getMethodsWithMaxParameters() {
        int max = getMaxParameters();
        return facts.getMethods().values().stream()
                .filter(m -> m.parameterCount() == max)
                .sorted(Comparator.comparing(MethodFact::methodKey))
                .toList();
    }

    // --- Règle générique du Top 10 % (§ 4.3) : k = ceil(n/10), ex-aequo retenus, 0 jamais retenu ---
    private <T> List<T> getTop10Percent(List<T> candidates, ToIntFunction<T> metricExtractor) {
        if (candidates.isEmpty()) return List.of();
        int k = (candidates.size() + 9) / 10;

        // Tri stable : à valeur égale, l'ordre (déterministe) des candidats est conservé
        List<T> sorted = new ArrayList<>(candidates);
        sorted.sort(Comparator.comparingInt(metricExtractor).reversed());
        int threshold = metricExtractor.applyAsInt(sorted.get(k - 1));

        return sorted.stream()
                .filter(item -> metricExtractor.applyAsInt(item) >= threshold)
                .filter(item -> metricExtractor.applyAsInt(item) > 0)
                .toList();
    }
}