package hai913i.tp1.utils;

import hai913i.tp1.model.infos.CallSiteInfo;
import hai913i.tp1.model.facts.*;
import hai913i.tp1.model.infos.ClassInfo;
import hai913i.tp1.visitors.CallExtractorVisitor;
import hai913i.tp1.visitors.StructureVisitor;

import java.util.*;

public final class FactBuilder {

    private FactBuilder() {}

    public static ProjectFacts build(StructureVisitor structVisitor, CallExtractorVisitor callVisitor) {
        Map<String, List<CallSiteInfo>> rawCallsByMethod = callVisitor.getCallsByMethod();
        Map<String, MethodFact> methodFacts = new TreeMap<>();
        Map<String, TypeFact> typeFacts = new TreeMap<>();

        // 1. Instancier les méthodes et leurs appels associés
        for (Map.Entry<String, List<CallSiteInfo>> entry : rawCallsByMethod.entrySet()) {
            String methodKey = entry.getKey();
            List<CallFact> calls = new ArrayList<>();

            for (CallSiteInfo raw : entry.getValue()) {
                calls.add(new CallFact(
                        raw.lineNumber,
                        raw.methodName,
                        raw.receiverStaticType,
                        raw.resolvedTarget
                ));
            }
            Collections.sort(calls);

            String declaringType = methodKey.contains("#") ? methodKey.substring(0, methodKey.indexOf('#')) : "";
            String afterHash = methodKey.contains("#") ? methodKey.substring(methodKey.indexOf('#') + 1) : methodKey;
            String simpleName = afterHash.contains("(") ? afterHash.substring(0, afterHash.indexOf('(')) : afterHash;
            boolean isConstructor = "<init>".equals(simpleName);

            int paramCount = 0;
            if (afterHash.contains("(") && afterHash.contains(")")) {
                String paramsStr = afterHash.substring(afterHash.indexOf('(') + 1, afterHash.indexOf(')')).trim();
                if (!paramsStr.isEmpty()) {
                    paramCount = paramsStr.split(",").length;
                }
            }

            methodFacts.put(methodKey, new MethodFact(
                    methodKey,
                    simpleName,
                    declaringType,
                    isConstructor,
                    paramCount,
                    calls
            ));
        }

        // 2. Instancier les types
        List<ClassInfo> sortedClasses = new ArrayList<>(structVisitor.getClasses());
        sortedClasses.sort(Comparator.comparing(c -> c.qualifiedName));

        for (ClassInfo ci : sortedClasses) {
            List<FieldFact> fields = new ArrayList<>();
            for (ClassInfo.FieldInfo f : ci.fields) {
                fields.add(new FieldFact(f.name, f.declaredType, f.visibility));
            }
            Collections.sort(fields);

            List<String> methodKeys = new ArrayList<>();
            for (String key : methodFacts.keySet()) {
                if (key.startsWith(ci.qualifiedName + "#")) {
                    methodKeys.add(key);
                }
            }
            Collections.sort(methodKeys);

            typeFacts.put(ci.qualifiedName, new TypeFact(
                    ci.qualifiedName,
                    ci.kind,
                    ci.packageName,
                    new ArrayList<>(ci.superClasses),
                    new ArrayList<>(ci.interfaces),
                    fields,
                    methodKeys
            ));
        }

        return new ProjectFacts(typeFacts, methodFacts);
    }
}