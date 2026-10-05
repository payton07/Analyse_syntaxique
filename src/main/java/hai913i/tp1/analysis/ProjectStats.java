package hai913i.tp1.analysis;

public class ProjectStats {
    private int regularClasses = 0;
    private int interfaces = 0;
    private int enums = 0;
    private int records = 0;

    private int methods = 0;
    private int constructors = 0;
    private int attributes = 0;

    public void incrementClasses() { regularClasses++; }
    public void incrementInterfaces() { interfaces++; }
    public void incrementEnums() { enums++; }
    public void incrementRecords() { records++; }

    public void incrementMethods() { methods++; }
    public void incrementConstructors() { constructors++; }
    public void addAttributes(int count) { attributes += count; }

    public int getTotalTypes() {
        return regularClasses + interfaces + enums + records;
    }

    public int getTotalMethods() {
        return methods + constructors;
    }

    @Override
    public String toString() {
        // Construction du détail des types (en tenant compte des pluriels et des zéros)
        StringBuilder typeDetails = new StringBuilder();
        typeDetails.append(regularClasses).append(" classe").append(regularClasses > 1 ? "s" : "");
        typeDetails.append(", ").append(interfaces).append(" interface").append(interfaces > 1 ? "s" : "");
        typeDetails.append(", ").append(enums).append(" énumération").append(enums > 1 ? "s" : "");
        if (records > 0) {
            typeDetails.append(", ").append(records).append(" record").append(records > 1 ? "s" : "");
        }

        // Format demandé :
        // 15 classes : 12 classes, 2 interfaces, 1 énumération; 59 méthodes, dont 9 constructeurs ; 21 attributs.
        return String.format("%d classes : %s; %d méthodes, dont %d constructeurs ; %d attributs.",
                getTotalTypes(),
                typeDetails,
                getTotalMethods(),
                constructors,
                attributes);
    }
}