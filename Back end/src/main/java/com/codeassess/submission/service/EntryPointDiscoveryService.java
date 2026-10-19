package com.codeassess.submission.service;

import com.codeassess.submission.dto.EntryPointResult;
import com.codeassess.submission.dto.ExtractedFile;
import com.codeassess.submission.dto.ExtractedWorkspace;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.Parameter;
import com.github.javaparser.ast.type.Type;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Inspects Java source files in an extracted project workspace using JavaParser
 * to discover executable {@code main} method entry points.
 *
 * <p>Supported main method signatures:
 * <ul>
 *   <li>{@code public static void main(String[] args)}</li>
 *   <li>{@code public static void main(String... args)}</li>
 *   <li>{@code public static void main(java.lang.String[] args)}</li>
 *   <li>{@code public static void main(java.lang.String... args)}</li>
 * </ul>
 */
@Service
public class EntryPointDiscoveryService {

    private static final Logger log = LoggerFactory.getLogger(EntryPointDiscoveryService.class);

    private static final Pattern JAVA_IDENTIFIER_PATTERN =
            Pattern.compile("^[a-zA-Z_$][a-zA-Z0-9_$]*(\\.[a-zA-Z_$][a-zA-Z0-9_$]*)*$");

    /**
     * Discovers the execution entry point across the workspace without a preferred class.
     *
     * @param workspace extracted submission workspace
     * @return {@link EntryPointResult} describing the outcome
     */
    public EntryPointResult discoverEntryPoint(ExtractedWorkspace workspace) {
        return discoverEntryPoint(workspace, null);
    }

    /**
     * Discovers the execution entry point across the workspace with an optional preferred class.
     *
     * @param workspace           extracted submission workspace
     * @param preferredEntryPoint optional class name requested by the exam or caller
     * @return {@link EntryPointResult} describing the outcome
     */
    public EntryPointResult discoverEntryPoint(ExtractedWorkspace workspace, String preferredEntryPoint) {
        if (workspace == null || workspace.files() == null || workspace.files().isEmpty()) {
            return EntryPointResult.none();
        }

        List<ExtractedFile> javaFiles = workspace.files().stream()
                .filter(ExtractedFile::isJavaSource)
                .toList();

        if (javaFiles.isEmpty()) {
            return EntryPointResult.none();
        }

        List<String> candidateClasses = findMainCandidates(javaFiles);
        return resolveEntryPoint(candidateClasses, preferredEntryPoint);
    }

    /**
     * In-memory scanner for testing or single-file collections.
     *
     * @param fileContentsMap     map of relative path to raw source code
     * @param preferredEntryPoint optional class name requested
     * @return {@link EntryPointResult} describing the outcome
     */
    public EntryPointResult discoverEntryPointFromMap(Map<String, String> fileContentsMap, String preferredEntryPoint) {
        if (fileContentsMap == null || fileContentsMap.isEmpty()) {
            return EntryPointResult.none();
        }

        List<String> candidateClasses = new ArrayList<>();
        for (Map.Entry<String, String> entry : fileContentsMap.entrySet()) {
            if (entry.getKey().toLowerCase(Locale.ROOT).endsWith(".java")) {
                scanSourceForMains(entry.getKey(), entry.getValue(), candidateClasses);
            }
        }
        Collections.sort(candidateClasses);
        return resolveEntryPoint(candidateClasses, preferredEntryPoint);
    }

    /**
     * Validates whether a string is a safe, syntactically valid Java class identifier.
     *
     * @param className class name to test
     * @return true if valid Java identifier; false if empty, contains spaces, or shell metacharacters
     */
    public boolean isValidJavaIdentifier(String className) {
        if (className == null || className.isBlank()) {
            return false;
        }
        return JAVA_IDENTIFIER_PATTERN.matcher(className.trim()).matches();
    }

    private List<String> findMainCandidates(List<ExtractedFile> javaFiles) {
        Set<String> candidateSet = new LinkedHashSet<>();

        for (ExtractedFile file : javaFiles) {
            try {
                String sourceCode = Files.readString(file.absolutePath(), StandardCharsets.UTF_8);
                scanSourceForMains(file.relativePath(), sourceCode, candidateSet);
            } catch (Exception e) {
                log.warn("Could not read or parse file {}: {}", file.relativePath(), e.getMessage());
            }
        }

        List<String> sortedCandidates = new ArrayList<>(candidateSet);
        Collections.sort(sortedCandidates);
        return sortedCandidates;
    }

    private void scanSourceForMains(String relativePath, String sourceCode, Collection<String> candidates) {
        CompilationUnit cu;
        try {
            cu = StaticJavaParser.parse(sourceCode);
        } catch (Exception e) {
            log.warn("Syntax parse warning for {}: {}", relativePath, e.getMessage());
            return;
        }

        String packageName = cu.getPackageDeclaration()
                .map(pd -> pd.getNameAsString())
                .orElse("");

        for (ClassOrInterfaceDeclaration classDecl : cu.findAll(ClassOrInterfaceDeclaration.class)) {
            if (classDecl.isInterface()) {
                continue;
            }

            for (MethodDeclaration method : classDecl.getMethods()) {
                if (isExecutableMainMethod(method)) {
                    String className = classDecl.getNameAsString();
                    String fqcn = packageName.isEmpty() ? className : packageName + "." + className;
                    if (isValidJavaIdentifier(fqcn)) {
                        candidates.add(fqcn);
                    }
                }
            }
        }
    }

    private boolean isExecutableMainMethod(MethodDeclaration method) {
        if (!"main".equals(method.getNameAsString())) {
            return false;
        }
        if (!method.isPublic() || !method.isStatic()) {
            return false;
        }
        if (!method.getType().asString().equals("void")) {
            return false;
        }
        if (method.getParameters().size() != 1) {
            return false;
        }

        Parameter param = method.getParameter(0);
        if (param.isVarArgs()) {
            String elementTypeName = param.getType().asString();
            return "String".equals(elementTypeName) || "java.lang.String".equals(elementTypeName);
        }

        Type paramType = param.getType();
        if (paramType.isArrayType()) {
            String typeStr = paramType.asString();
            return "String[]".equals(typeStr) || "java.lang.String[]".equals(typeStr);
        }

        return false;
    }

    private EntryPointResult resolveEntryPoint(List<String> candidates, String preferredEntryPoint) {
        if (preferredEntryPoint != null && !preferredEntryPoint.isBlank()) {
            String preferred = preferredEntryPoint.trim();
            if (!isValidJavaIdentifier(preferred)) {
                return EntryPointResult.invalidRequested(preferred, "Does not match valid Java class identifier syntax");
            }

            // Check exact fully qualified name or simple name match
            for (String candidate : candidates) {
                if (candidate.equals(preferred) || getSimpleClassName(candidate).equals(preferred)) {
                    return EntryPointResult.found(candidate, candidates);
                }
            }
            return EntryPointResult.invalidRequested(preferred,
                    "Not found among executable main classes: " + candidates);
        }

        if (candidates.isEmpty()) {
            return EntryPointResult.none();
        }

        if (candidates.size() == 1) {
            return EntryPointResult.found(candidates.get(0), candidates);
        }

        // Multiple candidates: apply documented deterministic selection rule
        // Rule 1: Check if exactly one candidate has simple name "Main"
        List<String> mains = candidates.stream()
                .filter(c -> "Main".equals(getSimpleClassName(c)))
                .toList();
        if (mains.size() == 1) {
            return EntryPointResult.found(mains.get(0), candidates);
        }

        // Rule 2: Check if exactly one candidate has simple name "App"
        List<String> apps = candidates.stream()
                .filter(c -> "App".equals(getSimpleClassName(c)))
                .toList();
        if (apps.size() == 1) {
            return EntryPointResult.found(apps.get(0), candidates);
        }

        // Ambiguous: never execute an arbitrary class
        return EntryPointResult.ambiguous(candidates);
    }

    private String getSimpleClassName(String fqcn) {
        int lastDot = fqcn.lastIndexOf('.');
        return lastDot >= 0 ? fqcn.substring(lastDot + 1) : fqcn;
    }
}
