package hai913i.tp1.model.infos;

public class CallSiteInfo {
    public String methodName;          // ex: checkOut, add
    public String receiverStaticType;  // ex: library.model.Item, library.service.Catalog
    public String resolvedTarget;      // ex: library.model.Item#checkOut() ou "non résolue"
    public int lineNumber;

    @Override
    public String toString() {
        return String.format("    - [Ligne %d] %s | Receveur: %s | Cible: %s",
                lineNumber, methodName, receiverStaticType, resolvedTarget);
    }
}