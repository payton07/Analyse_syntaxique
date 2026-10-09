package hai913i.tp1.utils;

import hai913i.tp1.model.infos.CallSiteInfo;
import hai913i.tp1.model.facts.*;
import hai913i.tp1.model.infos.ClassInfo;
import hai913i.tp1.parse.JdtParser.ParsedFile;
import hai913i.tp1.visitors.CallExtractorVisitor;
import hai913i.tp1.visitors.CountingRules;
import hai913i.tp1.visitors.StructureVisitor;
import org.eclipse.jdt.core.dom.*;

import java.util.*;

public final class FactBuilder {

    private FactBuilder() {}

    public static ProjectFacts build(List<ParsedFile> parsedFiles,
                                     StructureVisitor structVisitor,
                                     CallExtractorVisitor callVisitor) {

        // 1. Calcul des LOC par méthode, du total app et des paquetages depuis les unités AST
        Map<String, Integer> methodLocs = new HashMap<>();
        Map<String, Boolean> methodHasBody = new HashMap<>();
        Set<String> packages = new TreeSet<>();
        int totalAppLines = 0;

        for (ParsedFile pf : parsedFiles) {
            CompilationUnit cu = pf.unit();
            // Un fichier sans déclaration package appartient au paquetage « (défaut) » (§ 4.1)
            packages.add(cu.getPackage() != null
                    ? cu.getPackage().getName().getFullyQualifiedName()
                    : CountingRules.DEFAULT_PACKAGE);
            try {
                totalAppLines += java.nio.file.Files.readAllLines(pf.path()).size();
            } catch (java.io.IOException e) {
                totalAppLines += pf.unit().getLineNumber(pf.unit().getStartPosition() + pf.unit().getLength() - 1);
            }

            cu.accept(new ASTVisitor() {
                @Override
                public boolean visit(MethodDeclaration node) {
                    IMethodBinding mb = node.resolveBinding();
                    if (mb != null) {
                        String key = hai913i.tp1.utils.MethodSignatureUtil.getMethodKey(mb);
                        Block body = node.getBody();
                        if (body != null) {
                            int startLine = cu.getLineNumber(body.getStartPosition());
                            int endLine = cu.getLineNumber(body.getStartPosition() + body.getLength() - 1);
                            int loc = endLine - startLine + 1;
                            methodLocs.put(key, loc);
                            methodHasBody.put(key, true);
                        } else {
                            methodLocs.put(key, 0);
                            methodHasBody.put(key, false);
                        }
                    }
                    return super.visit(node);
                }
            });
        }

        // 2. Traitement des méthodes et de leurs appels
        Map<String, List<CallSiteInfo>> rawCallsByMethod = callVisitor.getCallsByMethod();
        Map<String, MethodFact> methodFacts = new TreeMap<>();

        for (Map.Entry<String, List<CallSiteInfo>> entry : rawCallsByMethod.entrySet()) {
            String methodKey = entry.getKey();
            List<CallFact> calls = new ArrayList<>();

            for (CallSiteInfo raw : entry.getValue()) {
                calls.add(new CallFact(raw.lineNumber, raw.methodName, raw.receiverStaticType, raw.resolvedTarget));
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

            int loc = methodLocs.getOrDefault(methodKey, 0);
            boolean hasBody = methodHasBody.getOrDefault(methodKey, false);

            methodFacts.put(methodKey, new MethodFact(
                    methodKey, simpleName, declaringType, isConstructor, paramCount, loc, hasBody, calls
            ));
        }

        // 3. Traitement des types
        Map<String, TypeFact> typeFacts = new TreeMap<>();
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
                    ci.qualifiedName, ci.kind, ci.packageName,
                    new ArrayList<>(ci.superClasses), new ArrayList<>(ci.interfaces),
                    fields, methodKeys
            ));
        }

        return new ProjectFacts(typeFacts, methodFacts, packages, totalAppLines);
    }
}