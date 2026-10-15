package com.codeassess.v2;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.regex.Pattern;

public class DatasetV2Generator {

    private final Path rootDir;
    private final Path v2Dir;
    private final Path baseDir;
    private final Path genDir;

    private static final String[] CATEGORIES = {"A", "B", "C", "D", "E1", "E3", "F1"};
    private static final int NUM_VARIANTS = 3;

    public DatasetV2Generator(Path rootDir) {
        this.rootDir = rootDir;
        this.v2Dir = rootDir.resolve("dataset").resolve("v2");
        this.baseDir = v2Dir.resolve("programs").resolve("base");
        this.genDir = v2Dir.resolve("programs").resolve("generated");
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        Path root = Paths.get(".").toAbsolutePath().normalize();
        if (root.getFileName().toString().equalsIgnoreCase("Back end")) {
            root = root.getParent();
        }
        System.out.println("Executing DatasetV2Generator from workspace root: " + root);
        DatasetV2Generator generator = new DatasetV2Generator(root);
        generator.generateAll();
    }

    public void generateAll() throws Exception {
        Files.createDirectories(genDir);

        List<BaseInfo> bases = List.of(
                new BaseInfo("BinarySearch", "BinarySearch.java"),
                new BaseInfo("BubbleSort", "BubbleSort.java"),
                new BaseInfo("MatrixMultiply", "MatrixMultiply.java"),
                new BaseInfo("SinglyLinkedList", "SinglyLinkedList.java"),
                new BaseInfo("PalindromeChecker", "PalindromeChecker.java")
        );

        System.out.println("1. Generating 105 controlled variants across 7 categories...");
        List<VariantInfo> variants = new ArrayList<>();
        for (BaseInfo base : bases) {
            String baseCode = Files.readString(baseDir.resolve(base.fileName), StandardCharsets.UTF_8);

            for (String cat : CATEGORIES) {
                for (int v = 1; v <= NUM_VARIANTS; v++) {
                    String variantName = base.name + "_" + cat + "_" + v;
                    String variantFileName = variantName + ".java";
                    String variantCode = transformCode(baseCode, base.name, cat, v, variantName);

                    Path varPath = genDir.resolve(variantFileName);
                    Files.writeString(varPath, variantCode, StandardCharsets.UTF_8);

                    variants.add(new VariantInfo(variantName, variantFileName, base.name, cat, v, varPath));
                }
            }
        }
        System.out.printf("   Generated %d variant Java files in %s%n", variants.size(), genDir);

        System.out.println("2. Generating dataset manifest.csv...");
        generateManifest(bases, variants);

        System.out.println("3. Generating ground-truth pairs.csv...");
        generatePairsCsv(bases, variants);

        System.out.println("=== DATASET V2.0 GENERATION COMPLETE ===");
    }

    private String transformCode(String baseCode, String baseName, String cat, int v, String variantName) {
        // Step 1: rename class declaration
        String code = baseCode.replace("public class " + baseName, "public class " + variantName);

        // Step 2: apply category-specific transformation
        switch (cat) {
            case "A" -> {
                // Exact copy except class name
                return code;
            }
            case "B" -> {
                // Systematic identifier renaming with word boundaries
                return applyIdentifierRenaming(code, baseName, v);
            }
            case "C" -> {
                // Comments and whitespace variations
                return applyCommentsAndFormatting(code, v);
            }
            case "D" -> {
                // Literal modifications (behavioral constants in main or defaults)
                return applyLiteralChanges(code, baseName, v);
            }
            case "E1" -> {
                // Benign dead-code insertion
                return applyDeadCodeInsertion(code, baseName, v);
            }
            case "E3" -> {
                // Equivalent block wrapping
                return applyBlockWrapping(code, baseName, v);
            }
            case "F1" -> {
                // Control flow transformation (for <-> while)
                return applyControlFlowTransformation(code, baseName, v);
            }
            default -> throw new IllegalArgumentException("Unknown category: " + cat);
        }
    }

    private String safeReplaceWord(String text, String word, String replacement) {
        return text.replaceAll("\\b" + Pattern.quote(word) + "\\b", replacement);
    }

    private String applyIdentifierRenaming(String code, String baseName, int v) {
        String result = code;
        switch (baseName) {
            case "BinarySearch" -> {
                String[] midNames = {"middle", "centerIndex", "pivotIndex"};
                String[] lowNames = {"leftIndex", "startBound", "minLimit"};
                String[] highNames = {"rightIndex", "endBound", "maxLimit"};
                String[] targetNames = {"searchKey", "queryValue", "wantedNum"};
                String[] arrNames = {"items", "sortedData", "elements"};
                result = safeReplaceWord(result, "target", targetNames[v - 1]);
                result = safeReplaceWord(result, "arr", arrNames[v - 1]);
                result = safeReplaceWord(result, "low", lowNames[v - 1]);
                result = safeReplaceWord(result, "high", highNames[v - 1]);
                result = safeReplaceWord(result, "mid", midNames[v - 1]);
            }
            case "BubbleSort" -> {
                String[] arrNames = {"values", "numberArray", "elementList"};
                String[] nNames = {"length", "totalItems", "itemCount"};
                String[] iNames = {"passIndex", "outerStep", "stage"};
                String[] jNames = {"innerIndex", "cursor", "pointer"};
                String[] tempNames = {"swapBuffer", "tempValue", "cacheVar"};
                result = safeReplaceWord(result, "arr", arrNames[v - 1]);
                result = safeReplaceWord(result, "n", nNames[v - 1]);
                result = safeReplaceWord(result, "temp", tempNames[v - 1]);
                result = safeReplaceWord(result, "i", iNames[v - 1]);
                result = safeReplaceWord(result, "j", jNames[v - 1]);
            }
            case "MatrixMultiply" -> {
                String[] aNames = {"firstMatrix", "matA", "leftGrid"};
                String[] bNames = {"secondMatrix", "matB", "rightGrid"};
                String[] prodNames = {"resultMatrix", "matrixProduct", "outputGrid"};
                String[] sumNames = {"accumulatedSum", "cellTotal", "runningTotal"};
                String[] kNames = {"kIdx", "stepK", "innerK"};
                String[] rNames = {"rCount", "numRows", "rowCount"};
                String[] cNames = {"cCount", "numCols", "colCount"};
                result = safeReplaceWord(result, "product", prodNames[v - 1]);
                result = safeReplaceWord(result, "sum", sumNames[v - 1]);
                result = safeReplaceWord(result, "a", aNames[v - 1]);
                result = safeReplaceWord(result, "b", bNames[v - 1]);
                result = safeReplaceWord(result, "k", kNames[v - 1]);
                result = safeReplaceWord(result, "rows", rNames[v - 1]);
                result = safeReplaceWord(result, "cols", cNames[v - 1]);
            }
            case "SinglyLinkedList" -> {
                String[] valNames = {"itemValue", "payload", "nodeData"};
                String[] nextNames = {"successor", "nextPointer", "forwardNode"};
                String[] currNames = {"traverser", "iteratorNode", "walkPtr"};
                String[] countNames = {"totalElements", "nodeCounter", "listLength"};
                result = safeReplaceWord(result, "val", valNames[v - 1]);
                result = safeReplaceWord(result, "next", nextNames[v - 1]);
                result = safeReplaceWord(result, "curr", currNames[v - 1]);
                result = safeReplaceWord(result, "count", countNames[v - 1]);
            }
            case "PalindromeChecker" -> {
                String[] textNames = {"candidateStr", "phrase", "testString"};
                String[] leftNames = {"leftCursor", "startPtr", "frontIndex"};
                String[] rightNames = {"rightCursor", "endPtr", "rearIndex"};
                result = safeReplaceWord(result, "text", textNames[v - 1]);
                result = safeReplaceWord(result, "left", leftNames[v - 1]);
                result = safeReplaceWord(result, "right", rightNames[v - 1]);
            }
        }
        return result;
    }

    private String applyCommentsAndFormatting(String code, int v) {
        String comment = switch (v) {
            case 1 -> "\n    // [Trans-C1] Inline explanatory documentation\n";
            case 2 -> "\n    /*\n     * [Trans-C2] Block comment describing algorithmic steps\n     */\n";
            case 3 -> "\n    // [Trans-C3] Code formatted with alternate indentation style\n";
            default -> "\n";
        };
        String res = code.replace("public static ", comment + "    public static ");
        if (v == 3) {
            res = res.replace("    ", "\t");
        }
        return res;
    }

    private String applyLiteralChanges(String code, String baseName, int v) {
        String result = code;
        switch (baseName) {
            case "BinarySearch" -> {
                int[] newTargets = {56, 72, 91};
                result = safeReplaceWord(result, "23", String.valueOf(newTargets[v - 1]));
            }
            case "BubbleSort" -> {
                String[] altArrays = {
                        "{99, 88, 77, 66, 55, 44, 33}",
                        "{10, 9, 8, 7, 6, 5, 4}",
                        "{50, 40, 30, 20, 10, 5, 1}"
                };
                result = result.replace("{64, 34, 25, 12, 22, 11, 90}", altArrays[v - 1]);
            }
            case "MatrixMultiply" -> {
                String[] altM1 = {"{{2, 3}, {4, 5}}", "{{3, 4}, {5, 6}}", "{{4, 5}, {6, 7}}"};
                result = result.replace("{{1, 2}, {3, 4}}", altM1[v - 1]);
            }
            case "SinglyLinkedList" -> {
                int[] offsets = {100, 200, 300};
                result = result.replace("new Node(10)", "new Node(" + (10 + offsets[v - 1]) + ")")
                        .replace("new Node(20)", "new Node(" + (20 + offsets[v - 1]) + ")")
                        .replace("new Node(30)", "new Node(" + (30 + offsets[v - 1]) + ")");
            }
            case "PalindromeChecker" -> {
                String[] words = {"\"radar\"", "\"level\"", "\"deified\""};
                result = result.replace("\"racecar\"", words[v - 1]);
            }
        }
        return result;
    }

    private String applyDeadCodeInsertion(String code, String baseName, int v) {
        String deadCode = switch (v) {
            case 1 -> "        if (false) { int unreachedFlag = 0; }\n";
            case 2 -> "        int dummyComputation = 100 * 2;\n";
            case 3 -> "        if (1 == 2) { String deadLog = \"never executed\"; }\n";
            default -> "";
        };
        return code.replace("public static void main(String[] args) {",
                "public static void main(String[] args) {\n" + deadCode);
    }

    private String applyBlockWrapping(String code, String baseName, int v) {
        if (baseName.equals("SinglyLinkedList")) {
            return code.replace("int total = countNodes(first);",
                    "int total;\n        {\n            total = countNodes(first);\n        }");
        } else if (baseName.equals("BinarySearch")) {
            return code.replace("int result = search(data, 23);",
                    "int result;\n        {\n            result = search(data, 23);\n        }");
        } else if (baseName.equals("BubbleSort")) {
            return code.replace("sort(data);",
                    "        {\n            sort(data);\n        }");
        } else if (baseName.equals("MatrixMultiply")) {
            return code.replace("int[][] res = multiply(m1, m2);",
                    "int[][] res;\n        {\n            res = multiply(m1, m2);\n        }");
        } else {
            return code.replace("boolean valid = isPalindrome(testWord);",
                    "boolean valid;\n        {\n            valid = isPalindrome(testWord);\n        }");
        }
    }

    private String applyControlFlowTransformation(String code, String baseName, int v) {
        String result = code;
        switch (baseName) {
            case "BubbleSort" -> {
                // Transform outer for-loop to while-loop
                result = """
public class %s {
    public static void sort(int[] arr) {
        int n = arr.length;
        int i = 0;
        while (i < n - 1) {
            for (int j = 0; j < n - i - 1; j++) {
                if (arr[j] > arr[j + 1]) {
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                }
            }
            i++;
        }
    }

    public static void main(String[] args) {
        int[] data = {64, 34, 25, 12, 22, 11, 90};
        sort(data);
        System.out.println("Sorted first element: " + data[0]);
    }
}
""".formatted(code.split("public class ")[1].split(" ")[0].trim());
            }
            case "MatrixMultiply" -> {
                // Transform outermost for-loop to while-loop
                result = """
public class %s {
    public static int[][] multiply(int[][] a, int[][] b) {
        int rows = a.length;
        int cols = b[0].length;
        int common = b.length;
        int[][] product = new int[rows][cols];
        int i = 0;
        while (i < rows) {
            for (int j = 0; j < cols; j++) {
                int sum = 0;
                for (int k = 0; k < common; k++) {
                    sum += a[i][k] * b[k][j];
                }
                product[i][j] = sum;
            }
            i++;
        }
        return product;
    }

    public static void main(String[] args) {
        int[][] m1 = {{1, 2}, {3, 4}};
        int[][] m2 = {{5, 6}, {7, 8}};
        int[][] res = multiply(m1, m2);
        System.out.println("Matrix product element: " + res[0][0]);
    }
}
""".formatted(code.split("public class ")[1].split(" ")[0].trim());
            }
            case "BinarySearch" -> {
                result = result.replace("while (low <= high) {", "for (; low <= high; ) {");
            }
            case "SinglyLinkedList" -> {
                result = result.replace("while (curr != null) {", "for (; curr != null; ) {");
            }
            case "PalindromeChecker" -> {
                result = result.replace("while (left < right) {", "for (; left < right; ) {");
            }
        }
        return result;
    }

    private void generateManifest(List<BaseInfo> bases, List<VariantInfo> variants) throws Exception {
        Path manifestPath = v2Dir.resolve("manifest.csv");
        int progId = 1;

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(manifestPath, StandardCharsets.UTF_8))) {
            pw.println("program_id,file_name,program_type,base_program,category,transformation,line_count,byte_count,sha256");

            // Bases
            for (BaseInfo b : bases) {
                Path p = baseDir.resolve(b.fileName);
                byte[] bytes = Files.readAllBytes(p);
                List<String> lines = Files.readAllLines(p, StandardCharsets.UTF_8);
                String sha256 = computeSha256(bytes);
                pw.printf("prog_%03d,%s,BASE,%s,NONE,none,%d,%d,%s%n",
                        progId++, b.fileName, b.name, lines.size(), bytes.length, sha256);
            }

            // Variants
            for (VariantInfo v : variants) {
                byte[] bytes = Files.readAllBytes(v.path);
                List<String> lines = Files.readAllLines(v.path, StandardCharsets.UTF_8);
                String sha256 = computeSha256(bytes);
                pw.printf("prog_%03d,%s,VARIANT,%s,%s,transformation_%s_%d,%d,%d,%s%n",
                        progId++, v.fileName, v.baseName, v.category, v.category, v.variantIndex,
                        lines.size(), bytes.length, sha256);
            }
        }
        System.out.printf("   Exported manifest to: %s (110 programs)%n", manifestPath);
    }

    private void generatePairsCsv(List<BaseInfo> bases, List<VariantInfo> variants) throws Exception {
        Path pairsPath = v2Dir.resolve("pairs.csv");
        int pairId = 1;

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(pairsPath, StandardCharsets.UTF_8))) {
            pw.println("pair_id,program_a,program_b,category,transformation,provenance,semantic_relationship,ground_truth");

            // 1. Positive pairs: Base vs its own Variants (5 bases * 21 variants = 105 positive pairs)
            for (BaseInfo base : bases) {
                for (VariantInfo v : variants) {
                    if (v.baseName.equals(base.name)) {
                        String semRel = switch (v.category) {
                            case "A" -> "EQUIVALENT_COPY";
                            case "B" -> "EQUIVALENT_RENAMED";
                            case "C" -> "EQUIVALENT_FORMATTED";
                            case "D" -> "MODIFIED_LITERALS";
                            case "E1" -> "EQUIVALENT_DEAD_CODE";
                            case "E3" -> "EQUIVALENT_RESTRUCTURED";
                            case "F1" -> "EQUIVALENT_CFG";
                            default -> "EQUIVALENT";
                        };

                        pw.printf("pair_%04d,%s,%s,%s,transformation_%s_%d,DERIVED,%s,PLAGIARISM%n",
                                pairId++, base.fileName, v.fileName, v.category, v.category, v.variantIndex, semRel);
                    }
                }
            }

            // 2. Negative pairs (Base vs Base): C(5, 2) = 10 pairs
            for (int i = 0; i < bases.size(); i++) {
                for (int j = i + 1; j < bases.size(); j++) {
                    pw.printf("pair_%04d,%s,%s,G,unrelated_base,INDEPENDENT,INDEPENDENT_ALGORITHM,NON_PLAGIARISM%n",
                            pairId++, bases.get(i).fileName, bases.get(j).fileName);
                }
            }

            // 3. Negative pairs (Base vs Unrelated Variant): 105 variants * 4 unrelated bases = 420 pairs
            for (VariantInfo v : variants) {
                for (BaseInfo base : bases) {
                    if (!base.name.equals(v.baseName)) {
                        pw.printf("pair_%04d,%s,%s,G,unrelated_variant,INDEPENDENT,INDEPENDENT_ALGORITHM,NON_PLAGIARISM%n",
                                pairId++, base.fileName, v.fileName);
                    }
                }
            }
        }
        System.out.printf("   Exported pairs.csv to: %s (535 total pairs: 105 PLAGIARISM + 430 NON_PLAGIARISM)%n", pairsPath);
    }

    private String computeSha256(byte[] data) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        byte[] hash = md.digest(data);
        StringBuilder sb = new StringBuilder();
        for (byte b : hash) {
            sb.append(String.format("%02x", b));
        }
        return sb.toString();
    }

    private record BaseInfo(String name, String fileName) {}

    private record VariantInfo(String name, String fileName, String baseName, String category, int variantIndex, Path path) {}
}
