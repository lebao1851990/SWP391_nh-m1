package com.codeassess.audit.v2;

import java.io.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.MessageDigest;
import java.util.*;
import java.util.stream.Collectors;

public class DatasetV2Auditor {

    private final Path rootDir;
    private final Path v2Dir;
    private final Path baseDir;
    private final Path genDir;
    private final Path baselineV2ResultsDir;
    private final Path auditDir;

    public DatasetV2Auditor(Path rootDir) {
        this.rootDir = rootDir;
        this.v2Dir = rootDir.resolve("dataset").resolve("v2");
        this.baseDir = v2Dir.resolve("programs").resolve("base");
        this.genDir = v2Dir.resolve("programs").resolve("generated");
        this.baselineV2ResultsDir = rootDir.resolve("dataset").resolve("experiments").resolve("dataset-v2-baseline").resolve("results");
        this.auditDir = rootDir.resolve("dataset").resolve("experiments").resolve("dataset-v2-audit");
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        Path root = Paths.get(".").toAbsolutePath().normalize();
        if (root.getFileName().toString().equalsIgnoreCase("Back end")) {
            root = root.getParent();
        }
        System.out.println("Running DatasetV2Auditor from: " + root);
        DatasetV2Auditor auditor = new DatasetV2Auditor(root);
        auditor.executeFullAudit();
    }

    public void executeFullAudit() throws Exception {
        Files.createDirectories(auditDir);

        System.out.println("==================================================");
        System.out.println("PHASE 1: CRYPTOGRAPHIC CHECKSUM & COMPILATION AUDIT");
        System.out.println("==================================================");
        auditChecksumsAndCompilation();

        System.out.println("\n==================================================");
        System.out.println("PHASE 2: PAIR CONSTRUCTION & PAIR-TYPE TAXONOMY");
        System.out.println("==================================================");
        auditPairConstruction();

        System.out.println("\n==================================================");
        System.out.println("PHASE 3: RAW EVALUATION RESULTS & METRIC AUDIT");
        System.out.println("==================================================");
        auditRawResultsAndMetrics();

        System.out.println("\n==================================================");
        System.out.println("PHASE 4: SCORE BOUNDARIES & SEPARATION AUDIT");
        System.out.println("==================================================");
        auditSeparationBoundaries();

        System.out.println("\n[AUDIT EXECUTION COMPLETE] All checks executed.");
    }

    private void auditChecksumsAndCompilation() throws Exception {
        Path manifestCsv = v2Dir.resolve("manifest.csv");
        List<String> lines = Files.readAllLines(manifestCsv, StandardCharsets.UTF_8);

        Path checkOut = auditDir.resolve("checksum_verification.csv");
        int matchCount = 0;
        int mismatchCount = 0;
        int missingCount = 0;

        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(checkOut, StandardCharsets.UTF_8))) {
            pw.println("program_id,file_name,program_type,recorded_sha256,actual_sha256,status");

            for (int i = 1; i < lines.size(); i++) {
                String line = lines.get(i).trim();
                if (line.isEmpty()) continue;
                String[] p = line.split(",");
                String progId = p[0];
                String fileName = p[1];
                String progType = p[2];
                String recordedSha = p[8];

                Path filePath = fileName.contains("_") ? genDir.resolve(fileName) : baseDir.resolve(fileName);
                if (!Files.exists(filePath)) {
                    pw.printf("%s,%s,%s,%s,NONE,FILE_NOT_FOUND%n", progId, fileName, progType, recordedSha);
                    missingCount++;
                    continue;
                }

                byte[] actualBytes = Files.readAllBytes(filePath);
                String actualSha = computeSha256(actualBytes);

                if (recordedSha.equalsIgnoreCase(actualSha)) {
                    pw.printf("%s,%s,%s,%s,%s,MATCH%n", progId, fileName, progType, recordedSha, actualSha);
                    matchCount++;
                } else {
                    pw.printf("%s,%s,%s,%s,%s,MISMATCH%n", progId, fileName, progType, recordedSha, actualSha);
                    mismatchCount++;
                }
            }
        }

        System.out.printf("Total files in manifest: %d%n", lines.size() - 1);
        System.out.printf("SHA-256 matches: %d | Mismatches: %d | Missing: %d%n", matchCount, mismatchCount, missingCount);
        System.out.printf("Exported: %s%n", checkOut);
    }

    private void auditPairConstruction() throws Exception {
        Path pairsCsv = v2Dir.resolve("pairs.csv");
        List<String> lines = Files.readAllLines(pairsCsv, StandardCharsets.UTF_8);

        Map<String, Integer> pairTypeCounts = new LinkedHashMap<>();
        Map<String, Map<String, Integer>> catByPairType = new LinkedHashMap<>();

        Set<String> uniquePairIds = new HashSet<>();
        Set<String> uniqueDirectedPairs = new HashSet<>();
        Set<String> uniqueUndirectedPairs = new HashSet<>();
        int selfPairCount = 0;
        int positiveCount = 0;
        int negativeCount = 0;

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] p = line.split(",");
            String pairId = p[0];
            String progA = p[1];
            String progB = p[2];
            String cat = p[3];
            String trans = p[4];
            String prov = p[5];
            String semRel = p[6];
            String groundTruth = p[7];

            uniquePairIds.add(pairId);
            uniqueDirectedPairs.add(progA + "->" + progB);
            String unordered = progA.compareTo(progB) < 0 ? progA + "<->" + progB : progB + "<->" + progA;
            uniqueUndirectedPairs.add(unordered);

            if (progA.equals(progB)) selfPairCount++;

            if ("PLAGIARISM".equalsIgnoreCase(groundTruth)) positiveCount++;
            else negativeCount++;

            // Categorize pair type
            String pairType;
            if (!progA.contains("_") && !progB.contains("_")) {
                pairType = "BASE_VS_BASE";
            } else if (!progA.contains("_") && progB.contains("_")) {
                String baseOfB = progB.split("_")[0];
                String baseOfA = progA.replace(".java", "");
                if (baseOfA.equals(baseOfB)) {
                    pairType = "BASE_VS_OWN_VARIANT";
                } else {
                    pairType = "BASE_VS_UNRELATED_VARIANT";
                }
            } else if (progA.contains("_") && !progB.contains("_")) {
                String baseOfA = progA.split("_")[0];
                String baseOfB = progB.replace(".java", "");
                if (baseOfA.equals(baseOfB)) {
                    pairType = "VARIANT_VS_OWN_BASE";
                } else {
                    pairType = "VARIANT_VS_UNRELATED_BASE";
                }
            } else {
                pairType = "VARIANT_VS_VARIANT";
            }

            pairTypeCounts.put(pairType, pairTypeCounts.getOrDefault(pairType, 0) + 1);

            catByPairType.computeIfAbsent(cat, k -> new TreeMap<>());
            Map<String, Integer> subMap = catByPairType.get(cat);
            subMap.put(pairType, subMap.getOrDefault(pairType, 0) + 1);
        }

        System.out.printf("Total evaluated pairs in pairs.csv: %d%n", lines.size() - 1);
        System.out.printf("Unique pair IDs: %d | Unique directed: %d | Unique undirected: %d%n",
                uniquePairIds.size(), uniqueDirectedPairs.size(), uniqueUndirectedPairs.size());
        System.out.printf("Self-pairs: %d%n", selfPairCount);
        System.out.printf("Positive pairs (PLAGIARISM): %d | Negative pairs (NON_PLAGIARISM): %d%n", positiveCount, negativeCount);

        System.out.println("\nPair Type Distribution in Dataset v2.0:");
        for (Map.Entry<String, Integer> e : pairTypeCounts.entrySet()) {
            System.out.printf("  - %-28s : %3d pairs%n", e.getKey(), e.getValue());
        }

        // Theoretical pair-space analysis:
        System.out.println("\nComplete Theoretical Program-Pair Universe (N = 110 files):");
        int totalCombinations = 110 * 109 / 2; // C(110, 2)
        int baseBaseTotal = 5 * 4 / 2; // C(5, 2) = 10
        int baseVarTotal = 5 * 105; // 525
        int baseOwnVarTotal = 5 * 21; // 105
        int baseUnrelatedVarTotal = 5 * (105 - 21); // 420
        int varVarTotal = 105 * 104 / 2; // C(105, 2) = 5460
        int varSameProblemTotal = 5 * (21 * 20 / 2); // 5 * 210 = 1050
        int varCrossProblemTotal = varVarTotal - varSameProblemTotal; // 4410

        System.out.printf("  - Total unordered pairs C(110, 2)      : %d%n", totalCombinations);
        System.out.printf("  - Base-vs-Base combinations C(5, 2)    : %d (Included in v2: %d)%n", baseBaseTotal, pairTypeCounts.getOrDefault("BASE_VS_BASE", 0));
        System.out.printf("  - Base-vs-Own-Variant (Positive)       : %d (Included in v2: %d)%n", baseOwnVarTotal, pairTypeCounts.getOrDefault("BASE_VS_OWN_VARIANT", 0));
        System.out.printf("  - Base-vs-Unrelated-Variant (Negative) : %d (Included in v2: %d)%n", baseUnrelatedVarTotal, pairTypeCounts.getOrDefault("BASE_VS_UNRELATED_VARIANT", 0));
        System.out.printf("  - Variant-vs-Variant (Same Problem)    : %d (Included in v2: 0 - Omitted)%n", varSameProblemTotal);
        System.out.printf("  - Variant-vs-Variant (Cross Problem)   : %d (Included in v2: 0 - Omitted)%n", varCrossProblemTotal);

        // Export pair_type_counts.csv
        Path ptOut = auditDir.resolve("pair_type_counts.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(ptOut, StandardCharsets.UTF_8))) {
            pw.println("pair_type,ground_truth,count_in_v2,theoretical_total,coverage_percent");
            pw.printf("BASE_VS_BASE,NON_PLAGIARISM,%d,%d,100.0%%%n", pairTypeCounts.getOrDefault("BASE_VS_BASE", 0), baseBaseTotal);
            pw.printf("BASE_VS_OWN_VARIANT,PLAGIARISM,%d,%d,100.0%%%n", pairTypeCounts.getOrDefault("BASE_VS_OWN_VARIANT", 0), baseOwnVarTotal);
            pw.printf("BASE_VS_UNRELATED_VARIANT,NON_PLAGIARISM,%d,%d,100.0%%%n", pairTypeCounts.getOrDefault("BASE_VS_UNRELATED_VARIANT", 0), baseUnrelatedVarTotal);
            pw.printf("VARIANT_VS_SAME_PROBLEM_VARIANT,PLAGIARISM,0,%d,0.0%%%n", varSameProblemTotal);
            pw.printf("VARIANT_VS_CROSS_PROBLEM_VARIANT,NON_PLAGIARISM,0,%d,0.0%%%n", varCrossProblemTotal);
            pw.printf("TOTAL_EVALUATED,MIXED,%d,%d,%.2f%%%n", lines.size() - 1, totalCombinations, (double) (lines.size() - 1) / totalCombinations * 100);
        }
        System.out.printf("Exported: %s%n", ptOut);
    }

    private void auditRawResultsAndMetrics() throws Exception {
        Path pairsCsv = v2Dir.resolve("pairs.csv");
        Path resultsCsv = baselineV2ResultsDir.resolve("baseline_v2_pair_results.csv");

        List<String> pairLines = Files.readAllLines(pairsCsv, StandardCharsets.UTF_8);
        List<String> resLines = Files.readAllLines(resultsCsv, StandardCharsets.UTF_8);

        List<Discrepancy> discrepancies = new ArrayList<>();

        if (pairLines.size() != resLines.size()) {
            discrepancies.add(new Discrepancy("FILE_LENGTH", "pairs.csv has " + pairLines.size() + " lines, results has " + resLines.size()));
        }

        int tp = 0, tn = 0, fp = 0, fn = 0;
        List<Double> positiveScores = new ArrayList<>();
        List<Double> negativeScores = new ArrayList<>();

        for (int i = 1; i < Math.min(pairLines.size(), resLines.size()); i++) {
            String[] pParts = pairLines.get(i).split(",");
            String[] rParts = resLines.get(i).split(",");

            String pId = pParts[0];
            String rId = rParts[0];
            if (!pId.equals(rId)) {
                discrepancies.add(new Discrepancy("ID_MISMATCH", "Line " + i + ": pair_id " + pId + " != " + rId));
            }

            String pGround = pParts[7];
            String rGround = rParts[7];
            if (!pGround.equals(rGround)) {
                discrepancies.add(new Discrepancy("GROUND_TRUTH_MISMATCH", "Line " + i + ": " + pGround + " != " + rGround));
            }

            double sim = Double.parseDouble(rParts[11]);
            boolean flag = Boolean.parseBoolean(rParts[12]);
            String outcome = rParts[13];

            if (sim < 0.0 || sim > 100.0) {
                discrepancies.add(new Discrepancy("INVALID_SIMILARITY", "Line " + i + ": sim=" + sim));
            }

            boolean expectedFlag = sim >= 70.00;
            if (flag != expectedFlag) {
                discrepancies.add(new Discrepancy("FLAG_MISMATCH", "Line " + i + ": flag=" + flag + ", expected=" + expectedFlag));
            }

            boolean isPlag = "PLAGIARISM".equalsIgnoreCase(pGround);
            String expectedOutcome;
            if (isPlag && expectedFlag) { expectedOutcome = "TP"; tp++; }
            else if (!isPlag && !expectedFlag) { expectedOutcome = "TN"; tn++; }
            else if (!isPlag && expectedFlag) { expectedOutcome = "FP"; fp++; }
            else { expectedOutcome = "FN"; fn++; }

            if (!outcome.equals(expectedOutcome)) {
                discrepancies.add(new Discrepancy("OUTCOME_MISMATCH", "Line " + i + ": outcome=" + outcome + ", expected=" + expectedOutcome));
            }

            if (isPlag) positiveScores.add(sim);
            else negativeScores.add(sim);
        }

        // Export discrepancies.csv
        Path discOut = auditDir.resolve("discrepancies.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(discOut, StandardCharsets.UTF_8))) {
            pw.println("type,description");
            for (Discrepancy d : discrepancies) {
                pw.printf("%s,%s%n", d.type, d.description);
            }
        }
        System.out.printf("Total discrepancies found: %d (Exported to %s)%n", discrepancies.size(), discOut);

        System.out.println("\nRecalculated Primary Confusion Matrix:");
        System.out.printf("  TP: %d | TN: %d | FP: %d | FN: %d%n", tp, tn, fp, fn);
        int total = tp + tn + fp + fn;
        double acc = (double) (tp + tn) / total;
        double prec = (tp + fp) == 0 ? 0.0 : (double) tp / (tp + fp);
        double rec = (tp + fn) == 0 ? 0.0 : (double) tp / (tp + fn);
        double spec = (tn + fp) == 0 ? 0.0 : (double) tn / (tn + fp);
        double f1 = (prec + rec) == 0.0 ? 0.0 : (2.0 * prec * rec) / (prec + rec);
        double fpr = (fp + tn) == 0 ? 0.0 : (double) fp / (fp + tn);
        double fnr = (fn + tp) == 0 ? 0.0 : (double) fn / (fn + tp);

        System.out.printf("  Accuracy:    %.4f (%.2f%%)%n", acc, acc * 100);
        System.out.printf("  Precision:   %.4f (%.2f%%)%n", prec, prec * 100);
        System.out.printf("  Recall:      %.4f (%.2f%%)%n", rec, rec * 100);
        System.out.printf("  Specificity: %.4f (%.2f%%)%n", spec, spec * 100);
        System.out.printf("  F1-Score:    %.4f%n", f1);
        System.out.printf("  FPR:         %.4f (%.2f%%)%n", fpr, fpr * 100);
        System.out.printf("  FNR:         %.4f (%.2f%%)%n", fnr, fnr * 100);
    }

    private void auditSeparationBoundaries() throws Exception {
        Path resultsCsv = baselineV2ResultsDir.resolve("baseline_v2_pair_results.csv");
        List<String> lines = Files.readAllLines(resultsCsv, StandardCharsets.UTF_8);

        double minPositiveSim = 100.0;
        double maxNegativeSim = 0.0;

        String minPosPair = "";
        String minPosDetails = "";

        String maxNegPair = "";
        String maxNegDetails = "";

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] p = line.split(",");

            String pairId = p[0];
            String progA = p[1];
            String progB = p[2];
            String cat = p[3];
            String trans = p[4];
            String groundTruth = p[7];
            double sim = Double.parseDouble(p[11]);

            if ("PLAGIARISM".equalsIgnoreCase(groundTruth)) {
                if (sim < minPositiveSim) {
                    minPositiveSim = sim;
                    minPosPair = pairId;
                    minPosDetails = String.format("%s vs %s (Cat: %s, Trans: %s)", progA, progB, cat, trans);
                }
            } else {
                if (sim > maxNegativeSim) {
                    maxNegativeSim = sim;
                    maxNegPair = pairId;
                    maxNegDetails = String.format("%s vs %s (Cat: %s, Trans: %s)", progA, progB, cat, trans);
                }
            }
        }

        System.out.println("Score Separation Audit Results:");
        System.out.printf("  Lowest Positive Score : %5.2f%% on %s | %s%n", minPositiveSim, minPosPair, minPosDetails);
        System.out.printf("  Highest Negative Score: %5.2f%% on %s | %s%n", maxNegativeSim, maxNegPair, maxNegDetails);
        double gap = minPositiveSim - maxNegativeSim;
        System.out.printf("  Score Separation Gap  : %5.2f percentage points%n", gap);

        // Export score_boundaries.csv
        Path bOut = auditDir.resolve("score_boundaries.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(bOut, StandardCharsets.UTF_8))) {
            pw.println("metric,pair_id,similarity_rate,details");
            pw.printf("LOWEST_POSITIVE,%s,%.2f,\"%s\"%n", minPosPair, minPositiveSim, minPosDetails);
            pw.printf("HIGHEST_NEGATIVE,%s,%.2f,\"%s\"%n", maxNegPair, maxNegativeSim, maxNegDetails);
            pw.printf("SEPARATION_GAP,N/A,%.2f,\"Separation between lowest positive and highest negative\"%n", gap);
        }
        System.out.printf("Exported: %s%n", bOut);
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

    private record Discrepancy(String type, String description) {}
}
