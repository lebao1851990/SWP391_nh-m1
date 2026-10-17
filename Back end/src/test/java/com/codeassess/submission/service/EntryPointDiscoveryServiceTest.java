package com.codeassess.submission.service;

import com.codeassess.submission.dto.EntryPointResult;
import com.codeassess.submission.dto.ExtractedFile;
import com.codeassess.submission.dto.ExtractedWorkspace;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class EntryPointDiscoveryServiceTest {

    private EntryPointDiscoveryService service;

    @BeforeEach
    void setUp() {
        service = new EntryPointDiscoveryService();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 1. Valid Signature Detection & Package Resolution
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Discovers single main method in default package")
    void defaultPackageMain_discovered() {
        String code = "public class Main {\n" +
                "    public static void main(String[] args) {\n" +
                "        System.out.println(1);\n" +
                "    }\n" +
                "}\n";

        EntryPointResult result = service.discoverEntryPointFromMap(Map.of("Main.java", code), null);

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.FOUND);
        assertThat(result.entryPointClass()).isEqualTo("Main");
        assertThat(result.candidates()).containsExactly("Main");
    }

    @Test
    @DisplayName("Discovers single main method with package declaration")
    void packagedMain_discoveredWithFqcn() {
        String code = "package com.codeassess.demo;\n" +
                "public class App {\n" +
                "    public static void main(String[] args) {}\n" +
                "}\n";

        EntryPointResult result = service.discoverEntryPointFromMap(Map.of("src/App.java", code), null);

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.FOUND);
        assertThat(result.entryPointClass()).isEqualTo("com.codeassess.demo.App");
    }

    @Test
    @DisplayName("Recognizes String... varargs main signature")
    void varargsSignature_recognized() {
        String code = "package test;\n" +
                "public class VarargsMain {\n" +
                "    public static void main(String... args) {}\n" +
                "}\n";

        EntryPointResult result = service.discoverEntryPointFromMap(Map.of("VarargsMain.java", code), null);

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.FOUND);
        assertThat(result.entryPointClass()).isEqualTo("test.VarargsMain");
    }

    @Test
    @DisplayName("Recognizes java.lang.String[] fully qualified parameter type")
    void fullyQualifiedParamType_recognized() {
        String code = "public class FQParam {\n" +
                "    public static void main(java.lang.String[] args) {}\n" +
                "}\n";

        EntryPointResult result = service.discoverEntryPointFromMap(Map.of("FQParam.java", code), null);

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.FOUND);
        assertThat(result.entryPointClass()).isEqualTo("FQParam");
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 2. No Main Method & Interfaces
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Returns NONE when no main method exists")
    void noMainMethod_returnsNone() {
        String code = "public class Helper {\n" +
                "    public int calculate(int x) { return x * 2; }\n" +
                "}\n";

        EntryPointResult result = service.discoverEntryPointFromMap(Map.of("Helper.java", code), null);

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.NONE);
        assertThat(result.entryPointClass()).isNull();
    }

    @Test
    @DisplayName("Ignores static main inside interface declarations")
    void interfaceWithMain_ignored() {
        String code = "public interface Task {\n" +
                "    static void main(String[] args) {}\n" +
                "}\n";

        EntryPointResult result = service.discoverEntryPointFromMap(Map.of("Task.java", code), null);

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.NONE);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 3. Ambiguity & Deterministic Selection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Multiple mains with non-standard names return AMBIGUOUS")
    void multipleMainsNonStandard_returnsAmbiguous() {
        String code1 = "public class ProblemA { public static void main(String[] args) {} }";
        String code2 = "public class ProblemB { public static void main(String[] args) {} }";

        EntryPointResult result = service.discoverEntryPointFromMap(
                Map.of("ProblemA.java", code1, "ProblemB.java", code2), null);

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.AMBIGUOUS);
        assertThat(result.entryPointClass()).isNull();
        assertThat(result.candidates()).containsExactly("ProblemA", "ProblemB");
    }

    @Test
    @DisplayName("Deterministic selection resolves class named Main when multiple candidates exist")
    void multipleMainsWithMainClass_resolvesMain() {
        String mainCode = "public class Main { public static void main(String[] args) {} }";
        String testCode = "public class HelperTest { public static void main(String[] args) {} }";

        EntryPointResult result = service.discoverEntryPointFromMap(
                Map.of("Main.java", mainCode, "HelperTest.java", testCode), null);

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.FOUND);
        assertThat(result.entryPointClass()).isEqualTo("Main");
    }

    @Test
    @DisplayName("Multiple Main classes in different packages return AMBIGUOUS")
    void multipleMainsConflictingPackages_returnsAmbiguous() {
        String code1 = "package pkgA; public class Main { public static void main(String[] args) {} }";
        String code2 = "package pkgB; public class Main { public static void main(String[] args) {} }";

        EntryPointResult result = service.discoverEntryPointFromMap(
                Map.of("pkgA/Main.java", code1, "pkgB/Main.java", code2), null);

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.AMBIGUOUS);
        assertThat(result.entryPointClass()).isNull();
        assertThat(result.candidates()).containsExactly("pkgA.Main", "pkgB.Main");
    }

    @Test
    @DisplayName("Preferred entry point selects matching candidate class")
    void preferredEntryPoint_matchesRequested() {
        String codeA = "package com.pkg; public class ClassA { public static void main(String[] args) {} }";
        String codeB = "package com.pkg; public class ClassB { public static void main(String[] args) {} }";

        EntryPointResult result = service.discoverEntryPointFromMap(
                Map.of("A.java", codeA, "B.java", codeB), "ClassB");

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.FOUND);
        assertThat(result.entryPointClass()).isEqualTo("com.pkg.ClassB");
    }

    @Test
    @DisplayName("Preferred entry point returns INVALID_REQUESTED when not in candidates")
    void preferredEntryPointMismatch_returnsInvalid() {
        String codeA = "public class ClassA { public static void main(String[] args) {} }";

        EntryPointResult result = service.discoverEntryPointFromMap(
                Map.of("A.java", codeA), "NonExistentClass");

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.INVALID_REQUESTED);
        assertThat(result.entryPointClass()).isNull();
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 4. Malformed Source & Malicious Identifier Injection
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Safely ignores malformed Java source without throwing exception")
    void malformedJavaSource_handledSafely() {
        String brokenCode = "public class Broken { syntax error here ;;;; }";
        String validCode = "public class Valid { public static void main(String[] args) {} }";

        EntryPointResult result = service.discoverEntryPointFromMap(
                Map.of("Broken.java", brokenCode, "Valid.java", validCode), null);

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.FOUND);
        assertThat(result.entryPointClass()).isEqualTo("Valid");
    }

    @Test
    @DisplayName("Rejects malicious entry-point names containing shell metacharacters")
    void maliciousIdentifier_rejected() {
        assertThat(service.isValidJavaIdentifier("Main; rm -rf /")).isFalse();
        assertThat(service.isValidJavaIdentifier("com.foo.Main`touch /tmp/pwn`")).isFalse();
        assertThat(service.isValidJavaIdentifier("com.foo.Main$(whoami)")).isFalse();
        assertThat(service.isValidJavaIdentifier("com/foo/Main")).isFalse();
        assertThat(service.isValidJavaIdentifier("123Illegal")).isFalse();
        assertThat(service.isValidJavaIdentifier("com.example.Valid_Class$Inner")).isTrue();

        EntryPointResult result = service.discoverEntryPointFromMap(
                Map.of("Main.java", "public class Main { public static void main(String[] args) {} }"),
                "Main; rm -rf /");

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.INVALID_REQUESTED);
    }

    // ─────────────────────────────────────────────────────────────────────────
    // 5. ExtractedWorkspace Integration
    // ─────────────────────────────────────────────────────────────────────────

    @Test
    @DisplayName("Scans ExtractedWorkspace on filesystem correctly")
    void extractedWorkspace_scannedSuccessfully(@TempDir Path tempDir) throws IOException {
        Path srcDir = Files.createDirectories(tempDir.resolve("src"));
        Path mainFile = srcDir.resolve("WorkspaceMain.java");
        Files.writeString(mainFile, "package app; public class WorkspaceMain { public static void main(String[] args) {} }");

        ExtractedFile extractedFile = new ExtractedFile("src/WorkspaceMain.java", mainFile, Files.size(mainFile), true);
        ExtractedWorkspace workspace = new ExtractedWorkspace(tempDir, List.of(extractedFile), Files.size(mainFile), "dummyhash");

        EntryPointResult result = service.discoverEntryPoint(workspace);

        assertThat(result.status()).isEqualTo(EntryPointResult.Status.FOUND);
        assertThat(result.entryPointClass()).isEqualTo("app.WorkspaceMain");
    }
}
