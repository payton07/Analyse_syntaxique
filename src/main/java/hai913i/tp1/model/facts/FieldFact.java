package hai913i.tp1.model.facts;

public record FieldFact(
        String name,
        String declaredType,
        String visibility
) implements Comparable<FieldFact> {

    @Override
    public int compareTo(FieldFact o) {
        return this.name.compareTo(o.name);
    }
}