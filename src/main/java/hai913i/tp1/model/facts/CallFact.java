package hai913i.tp1.model.facts;

public record CallFact(
        int lineNumber,
        String methodName,
        String receiverStaticType,
        String resolvedTarget
) implements Comparable<CallFact> {

    public boolean isResolved() {
        return resolvedTarget != null && !resolvedTarget.equals("non résolue");
    }

    @Override
    public int compareTo(CallFact o) {
        int cmp = Integer.compare(this.lineNumber, o.lineNumber);
        if (cmp != 0) return cmp;
        cmp = this.methodName.compareTo(o.methodName);
        if (cmp != 0) return cmp;
        return this.resolvedTarget.compareTo(o.resolvedTarget);
    }
}