package com.codeassess.audit;

import com.codeassess.plagiarism.normalizer.AstNormalizer;
import com.codeassess.plagiarism.parser.AstNode;
import com.codeassess.plagiarism.parser.JavaAstParser;
import com.codeassess.plagiarism.similarity.JaccardSimilarityCalculator;
import com.codeassess.plagiarism.similarity.SimilarityResult;
import com.codeassess.plagiarism.tokenizer.KGram;
import com.codeassess.plagiarism.tokenizer.KGramGenerator;
import com.codeassess.plagiarism.winnowing.FingerprintHasher;
import com.codeassess.plagiarism.winnowing.HashedKGram;
import com.codeassess.plagiarism.winnowing.Winnower;
import com.github.javaparser.ast.CompilationUnit;

import java.io.*;
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class AuditRunner {

    private final Path rootDir;
    private final Path datasetDir;
    private final Path auditDir;
    private final Path run2Dir;
    private final Path baselineResultsDir;

    private final JavaAstParser parser = new JavaAstParser();
    private final AstNormalizer normalizer = new AstNormalizer();
    private final KGramGenerator kGramGenerator = new KGramGenerator(5);
    private final FingerprintHasher hasher = new FingerprintHasher();
    private final Winnower winnower = new Winnower(4);
    private final JaccardSimilarityCalculator calculator = new JaccardSimilarityCalculator();

    private final Map<String, Set<Long>> fingerprintCache = new HashMap<>();

    public AuditRunner(Path rootDir) {
        this.rootDir = rootDir;
        this.datasetDir = rootDir.resolve("dataset");
        this.auditDir = datasetDir.resolve("experiments").resolve("audit-v1");
        this.run2Dir = datasetDir.resolve("experiments").resolve("audit-v1-run-2");
        this.baselineResultsDir = datasetDir.resolve("experiments").resolve("baseline-v1").resolve("results");
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        Path root = Paths.get(".").toAbsolutePath().normalize();
        if (root.getFileName().toString().equalsIgnoreCase("Back end")) {
            root = root.getParent();
        }
        System.out.println("Running AuditRunner from workspace root: " + root);
        AuditRunner runner = new AuditRunner(root);
        runner.executeAudit();
    }

    public void executeAudit() throws Exception {
        Files.createDirectories(auditDir);
        Files.createDirectories(run2Dir);

        System.out.println("==================================================");
        System.out.println("STAGE 1: AUDITING DATASET V1 STRUCTURE & PAIRS.CSV");
        System.out.println("==================================================");
        auditDatasetStructure();

        System.out.println("\n==================================================");
        System.out.println("STAGE 2: INVESTIGATING THE 5 BASE PROGRAMS");
        System.out.println("==================================================");
        auditBasePrograms();

        System.out.println("\n==================================================");
        System.out.println("STAGE 3: AUDITING FALSE POSITIVES (370 PAIRS)");
        System.out.println("==================================================");
        auditFalsePositives();

        System.out.println("\n==================================================");
        System.out.println("STAGE 4: AUDITING FALSE NEGATIVES (15 F1 PAIRS)");
        System.out.println("==================================================");
        auditFalseNegatives();

        System.out.println("\n==================================================");
        System.out.println("STAGE 5: RECALCULATING ALL THRESHOLD METRICS");
        System.out.println("==================================================");
        recalculateThresholdMetrics();

        System.out.println("\n==================================================");
        System.out.println("STAGE 6: VERIFYING REPRODUCIBILITY (RUN-2)");
        System.out.println("==================================================");
        verifyReproducibilityRun2();

        System.out.println("\n[SUCCESS] All audit calculations and CSV generations completed successfully.");
    }

    private void auditDatasetStructure() throws Exception {
        Path baseDir = datasetDir.resolve("programs").resolve("base");
        Path genDir = datasetDir.resolve("programs").resolve("generated");
        Path pairsCsv = datasetDir.resolve("ground-truth").resolve("pairs.csv");

        List<Path> baseFiles = Files.list(baseDir).filter(p -> p.toString().endsWith(".java")).sorted().toList();
        List<Path> genFiles = Files.list(genDir).filter(p -> p.toString().endsWith(".java")).sorted().toList();

        System.out.printf("Base Java files count: %d%n", baseFiles.size());
        System.out.printf("Generated Java files count: %d%n", genFiles.size());
        System.out.printf("Total Java files: %d%n", baseFiles.size() + genFiles.size());

        List<String> lines = Files.readAllLines(pairsCsv, StandardCharsets.UTF_8);
        System.out.printf("Total lines in pairs.csv: %d (header + %d rows)%n", lines.size(), lines.size() - 1);

        String header = lines.get(0);
        System.out.printf("Header: %s%n", header);

        Set<String> uniquePairIds = new HashSet<>();
        Set<String> uniquePairTuples = new HashSet<>();
        Set<String> unorderedPairTuples = new HashSet<>();
        int positiveCount = 0;
        int negativeCount = 0;
        int malformedCount = 0;
        int missingFileCount = 0;
        int selfPairCount = 0;

        List<ValidatedPair> validatedPairs = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            if (parts.length != 6) {
                System.err.printf("Malformed line %d: %s%n", i, line);
                malformedCount++;
                continue;
            }
            String pairId = parts[0];
            String progA = parts[1];
            String progB = parts[2];
            String cat = parts[3];
            String trans = parts[4];
            String label = parts[5];

            if (!uniquePairIds.add(pairId)) {
                System.err.printf("Duplicate pairId: %s at line %d%n", pairId, i);
            }

            String tuple = progA + "->" + progB;
            if (!uniquePairTuples.add(tuple)) {
                System.err.printf("Duplicate directed pair: %s at line %d%n", tuple, i);
            }

            String unorderedTuple = progA.compareTo(progB) < 0 ? progA + "<->" + progB : progB + "<->" + progA;
            unorderedPairTuples.add(unorderedTuple);

            if (progA.equals(progB)) {
                System.err.printf("Self pair detected: %s vs %s at line %d%n", progA, progB, i);
                selfPairCount++;
            }

            Path pathA = progA.startsWith("Base") ? baseDir.resolve(progA) : genDir.resolve(progA);
            Path pathB = progB.startsWith("Base") ? baseDir.resolve(progB) : genDir.resolve(progB);
            if (!Files.exists(pathA)) {
                System.err.printf("File A missing: %s at line %d%n", progA, i);
                missingFileCount++;
            }
            if (!Files.exists(pathB)) {
                System.err.printf("File B missing: %s at line %d%n", progB, i);
                missingFileCount++;
            }

            if ("PLAGIARISM".equalsIgnoreCase(label)) {
                positiveCount++;
            } else if ("NON_PLAGIARISM".equalsIgnoreCase(label)) {
                negativeCount++;
            } else {
                System.err.printf("Invalid ground truth label: %s at line %d%n", label, i);
            }

            validatedPairs.add(new ValidatedPair(pairId, progA, progB, cat, trans, label,
                    Files.exists(pathA) && Files.exists(pathB), !progA.equals(progB)));
        }

        System.out.printf("Validation summary:%n");
        System.out.printf("- Valid rows: %d%n", validatedPairs.size());
        System.out.printf("- Positive pairs (PLAGIARISM): %d%n", positiveCount);
        System.out.printf("- Negative pairs (NON_PLAGIARISM): %d%n", negativeCount);
        System.out.printf("- Unique directed pairs: %d%n", uniquePairTuples.size());
        System.out.printf("- Unique undirected pairs: %d%n", unorderedPairTuples.size());
        System.out.printf("- Self pairs: %d%n", selfPairCount);
        System.out.printf("- Missing file references: %d%n", missingFileCount);
        System.out.printf("- Malformed rows: %d%n", malformedCount);

        // Export validated_pairs.csv
        Path outCsv = auditDir.resolve("validated_pairs.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outCsv, StandardCharsets.UTF_8))) {
            pw.println("pair_id,program_a,program_b,category,transformation,ground_truth,files_exist,is_distinct_pair");
            for (ValidatedPair vp : validatedPairs) {
                pw.printf("%s,%s,%s,%s,%s,%s,%b,%b%n",
                        vp.pairId, vp.progA, vp.progB, vp.cat, vp.trans, vp.label, vp.filesExist, vp.isDistinct);
            }
        }
        System.out.printf("Exported: %s%n", outCsv);
    }

    private void auditBasePrograms() throws Exception {
        Path baseDir = datasetDir.resolve("programs").resolve("base");
        List<String> baseNames = List.of("Base1.java", "Base2.java", "Base3.java", "Base4.java", "Base5.java");

        Map<String, List<AstNode>> normalizedSeqMap = new LinkedHashMap<>();
        Map<String, List<KGram>> kGramsMap = new LinkedHashMap<>();
        Map<String, Set<Long>> hashesMap = new LinkedHashMap<>();

        for (String name : baseNames) {
            Path p = baseDir.resolve(name);
            String code = Files.readString(p, StandardCharsets.UTF_8);
            CompilationUnit cu = parser.parse(code);
            List<AstNode> seq = normalizer.normalize(cu);
            List<KGram> kgrams = kGramGenerator.generateKGrams(seq);

            List<HashedKGram> hashed = new ArrayList<>();
            for (int i = 0; i < kgrams.size(); i++) {
                KGram kg = kgrams.get(i);
                long h = hasher.hash(kg.getConcatenatedTypeString());
                hashed.add(new HashedKGram(h, i, kg.getRepresentativeNodeType(), kg.getStartLine(), kg.getEndLine()));
            }
            List<HashedKGram> winnowed = winnower.winnow(hashed);
            Set<Long> hashSet = winnowed.stream().map(HashedKGram::getHash).collect(Collectors.toSet());

            normalizedSeqMap.put(name, seq);
            kGramsMap.put(name, kgrams);
            hashesMap.put(name, hashSet);
        }

        System.out.println("Comparing AST node sequences across Base1..Base5:");
        List<AstNode> base1Seq = normalizedSeqMap.get("Base1.java");
        System.out.printf("Base1 normalized AST node count: %d%n", base1Seq.size());
        for (int i = 0; i < base1Seq.size(); i++) {
            System.out.printf("  [%02d] %s%n", i, base1Seq.get(i).getNodeType());
        }

        boolean allIdentical = true;
        for (String name : baseNames) {
            List<AstNode> seq = normalizedSeqMap.get(name);
            if (seq.size() != base1Seq.size()) {
                allIdentical = false;
                System.err.printf("Mismatch in size for %s: %d vs %d%n", name, seq.size(), base1Seq.size());
            } else {
                for (int i = 0; i < seq.size(); i++) {
                    if (!seq.get(i).getNodeType().equals(base1Seq.get(i).getNodeType())) {
                        allIdentical = false;
                        System.err.printf("Mismatch in token %d for %s: %s vs %s%n",
                                i, name, seq.get(i).getNodeType(), base1Seq.get(i).getNodeType());
                    }
                }
            }
        }
        System.out.printf("Are all 5 base programs' normalized AST sequences 100%% identical? %b%n", allIdentical);

        System.out.printf("Base1 fingerprint count: %d hashes%n", hashesMap.get("Base1.java").size());
        for (Long h : hashesMap.get("Base1.java")) {
            System.out.printf("  Hash: %d (0x%016X)%n", h, h);
        }

        System.out.println("Pairwise Jaccard similarity across all 10 base-vs-base combinations:");
        for (int i = 0; i < baseNames.size(); i++) {
            for (int j = i + 1; j < baseNames.size(); j++) {
                String bA = baseNames.get(i);
                String bB = baseNames.get(j);
                SimilarityResult sr = calculator.calculate(hashesMap.get(bA), hashesMap.get(bB));
                System.out.printf("  %s vs %s: Shared=%d, Sim=%.2f%%, Flagged=%b%n",
                        bA, bB, sr.getSharedHashesCount(), sr.getSimilarityRate().doubleValue(), sr.isFlagged());
            }
        }
    }

    private void auditFalsePositives() throws Exception {
        Path baselineCsv = baselineResultsDir.resolve("baseline_pair_results.csv");
        List<String> lines = Files.readAllLines(baselineCsv, StandardCharsets.UTF_8);

        List<PairResultLine> fpList = new ArrayList<>();
        Map<String, Integer> fpByGroup = new TreeMap<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            PairResultLine r = new PairResultLine(
                    parts[0], parts[1], parts[2], parts[3], parts[4], parts[5],
                    Integer.parseInt(parts[6]), Integer.parseInt(parts[7]), Integer.parseInt(parts[8]),
                    Double.parseDouble(parts[9]), Boolean.parseBoolean(parts[10]), parts[11]);

            if ("FP".equals(r.outcome)) {
                fpList.add(r);

                // Group categorization
                String group;
                if (r.progA.startsWith("Base") && r.progB.startsWith("Base")) {
                    group = "Base-vs-Base";
                } else if (r.progB.contains("_A_")) {
                    group = "Base-vs-Variant_A";
                } else if (r.progB.contains("_B_")) {
                    group = "Base-vs-Variant_B";
                } else if (r.progB.contains("_C_")) {
                    group = "Base-vs-Variant_C";
                } else if (r.progB.contains("_D_")) {
                    group = "Base-vs-Variant_D";
                } else if (r.progB.contains("_E1_")) {
                    group = "Base-vs-Variant_E1";
                } else if (r.progB.contains("_E3_")) {
                    group = "Base-vs-Variant_E3";
                } else if (r.progB.contains("_F1_")) {
                    group = "Base-vs-Variant_F1";
                } else {
                    group = "Other";
                }
                fpByGroup.put(group, fpByGroup.getOrDefault(group, 0) + 1);
            }
        }

        System.out.printf("Total False Positives identified: %d%n", fpList.size());
        System.out.println("False Positive distribution by structural pairing group:");
        for (Map.Entry<String, Integer> e : fpByGroup.entrySet()) {
            System.out.printf("  - %-22s : %3d pairs%n", e.getKey(), e.getValue());
        }

        // Export fp_pairs.csv
        Path outFp = auditDir.resolve("fp_pairs.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outFp, StandardCharsets.UTF_8))) {
            pw.println("pair_id,program_a,program_b,category,transformation,ground_truth,hashes_a,hashes_b,shared_hashes,similarity_rate,predicted_flag,outcome,fp_group");
            for (PairResultLine r : fpList) {
                String group;
                if (r.progA.startsWith("Base") && r.progB.startsWith("Base")) group = "Base-vs-Base";
                else if (r.progB.contains("_A_")) group = "Base-vs-Variant_A";
                else if (r.progB.contains("_B_")) group = "Base-vs-Variant_B";
                else if (r.progB.contains("_C_")) group = "Base-vs-Variant_C";
                else if (r.progB.contains("_D_")) group = "Base-vs-Variant_D";
                else if (r.progB.contains("_E1_")) group = "Base-vs-Variant_E1";
                else if (r.progB.contains("_E3_")) group = "Base-vs-Variant_E3";
                else if (r.progB.contains("_F1_")) group = "Base-vs-Variant_F1";
                else group = "Other";

                pw.printf("%s,%s,%s,%s,%s,%s,%d,%d,%d,%.2f,%b,%s,%s%n",
                        r.pairId, r.progA, r.progB, r.category, r.trans, r.groundTruth,
                        r.hashesA, r.hashesB, r.sharedHashes, r.similarityRate, r.predictedFlag, r.outcome, group);
            }
        }
        System.out.printf("Exported: %s%n", outFp);
    }

    private void auditFalseNegatives() throws Exception {
        Path baselineCsv = baselineResultsDir.resolve("baseline_pair_results.csv");
        List<String> lines = Files.readAllLines(baselineCsv, StandardCharsets.UTF_8);

        List<PairResultLine> fnList = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            PairResultLine r = new PairResultLine(
                    parts[0], parts[1], parts[2], parts[3], parts[4], parts[5],
                    Integer.parseInt(parts[6]), Integer.parseInt(parts[7]), Integer.parseInt(parts[8]),
                    Double.parseDouble(parts[9]), Boolean.parseBoolean(parts[10]), parts[11]);
            if ("FN".equals(r.outcome)) {
                fnList.add(r);
            }
        }

        System.out.printf("Total False Negatives identified: %d%n", fnList.size());
        for (PairResultLine r : fnList) {
            System.out.printf("  %s: %s vs %s => Sim: %.2f%%, Shared: %d, HashesA: %d, HashesB: %d%n",
                    r.pairId, r.progA, r.progB, r.similarityRate, r.sharedHashes, r.hashesA, r.hashesB);
        }

        // Export fn_pairs.csv
        Path outFn = auditDir.resolve("fn_pairs.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outFn, StandardCharsets.UTF_8))) {
            pw.println("pair_id,program_a,program_b,category,transformation,ground_truth,hashes_a,hashes_b,shared_hashes,similarity_rate,predicted_flag,outcome");
            for (PairResultLine r : fnList) {
                pw.printf("%s,%s,%s,%s,%s,%s,%d,%d,%d,%.2f,%b,%s%n",
                        r.pairId, r.progA, r.progB, r.category, r.trans, r.groundTruth,
                        r.hashesA, r.hashesB, r.sharedHashes, r.similarityRate, r.predictedFlag, r.outcome);
            }
        }
        System.out.printf("Exported: %s%n", outFn);

        // Detailed AST structural breakdown for Base1 vs Variant_1_F1_1
        System.out.println("\n--- Deep Dive: Base1.java vs Variant_1_F1_1.java ---");
        Path base1Path = datasetDir.resolve("programs").resolve("base").resolve("Base1.java");
        Path f1Path = datasetDir.resolve("programs").resolve("generated").resolve("Variant_1_F1_1.java");

        String codeBase1 = Files.readString(base1Path, StandardCharsets.UTF_8);
        String codeF1 = Files.readString(f1Path, StandardCharsets.UTF_8);

        List<AstNode> seqBase1 = normalizer.normalize(parser.parse(codeBase1));
        List<AstNode> seqF1 = normalizer.normalize(parser.parse(codeF1));

        System.out.printf("Base1 AST node count: %d%n", seqBase1.size());
        System.out.printf("F1 AST node count: %d%n", seqF1.size());

        List<KGram> kgBase1 = kGramGenerator.generateKGrams(seqBase1);
        List<KGram> kgF1 = kGramGenerator.generateKGrams(seqF1);

        System.out.printf("Base1 5-grams count: %d%n", kgBase1.size());
        System.out.printf("F1 5-grams count: %d%n", kgF1.size());

        List<HashedKGram> hBase1 = new ArrayList<>();
        for (int i = 0; i < kgBase1.size(); i++) {
            hBase1.add(new HashedKGram(hasher.hash(kgBase1.get(i).getConcatenatedTypeString()), i,
                    kgBase1.get(i).getRepresentativeNodeType(), kgBase1.get(i).getStartLine(), kgBase1.get(i).getEndLine()));
        }

        List<HashedKGram> hF1 = new ArrayList<>();
        for (int i = 0; i < kgF1.size(); i++) {
            hF1.add(new HashedKGram(hasher.hash(kgF1.get(i).getConcatenatedTypeString()), i,
                    kgF1.get(i).getRepresentativeNodeType(), kgF1.get(i).getStartLine(), kgF1.get(i).getEndLine()));
        }

        List<HashedKGram> wBase1 = winnower.winnow(hBase1);
        List<HashedKGram> wF1 = winnower.winnow(hF1);

        Set<Long> setBase1 = wBase1.stream().map(HashedKGram::getHash).collect(Collectors.toSet());
        Set<Long> setF1 = wF1.stream().map(HashedKGram::getHash).collect(Collectors.toSet());

        Set<Long> intersection = new HashSet<>(setBase1);
        intersection.retainAll(setF1);

        Set<Long> union = new HashSet<>(setBase1);
        union.addAll(setF1);

        System.out.printf("Winnowed hashes in Base1: %d%n", setBase1.size());
        System.out.printf("Winnowed hashes in F1: %d%n", setF1.size());
        System.out.printf("Intersection count: %d%n", intersection.size());
        System.out.printf("Union count: %d%n", union.size());
        double exactSim = (double) intersection.size() / union.size() * 100.0;
        System.out.printf("Exact Jaccard Similarity: %d / %d = %.4f%% (Formatted: %.2f%%)%n",
                intersection.size(), union.size(), exactSim, exactSim);

        System.out.println("Shared hashes (Intersection):");
        for (Long h : intersection) {
            System.out.printf("  0x%016X (%d)%n", h, h);
        }

        Set<Long> onlyBase1 = new HashSet<>(setBase1);
        onlyBase1.removeAll(setF1);
        System.out.printf("Hashes in Base1 only (%d):%n", onlyBase1.size());
        for (Long h : onlyBase1) {
            System.out.printf("  0x%016X (%d)%n", h, h);
        }

        Set<Long> onlyF1 = new HashSet<>(setF1);
        onlyF1.removeAll(setBase1);
        System.out.printf("Hashes in F1 only (%d):%n", onlyF1.size());
        for (Long h : onlyF1) {
            System.out.printf("  0x%016X (%d)%n", h, h);
        }
    }

    private void recalculateThresholdMetrics() throws Exception {
        Path baselineCsv = baselineResultsDir.resolve("baseline_pair_results.csv");
        List<String> lines = Files.readAllLines(baselineCsv, StandardCharsets.UTF_8);

        List<PairResultLine> results = new ArrayList<>();
        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            results.add(new PairResultLine(
                    parts[0], parts[1], parts[2], parts[3], parts[4], parts[5],
                    Integer.parseInt(parts[6]), Integer.parseInt(parts[7]), Integer.parseInt(parts[8]),
                    Double.parseDouble(parts[9]), Boolean.parseBoolean(parts[10]), parts[11]));
        }

        double[] thresholds = {10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 65.0, 70.0, 75.0, 80.0, 85.0, 90.0, 95.0, 100.0};

        Path outCsv = auditDir.resolve("recalculated_threshold_metrics.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(outCsv, StandardCharsets.UTF_8))) {
            pw.println("threshold,tp,tn,fp,fn,accuracy,precision,recall,f1,specificity,fpr,fnr");

            for (double t : thresholds) {
                int tp = 0, tn = 0, fp = 0, fn = 0;
                for (PairResultLine r : results) {
                    boolean isPlag = "PLAGIARISM".equalsIgnoreCase(r.groundTruth);
                    // Crucial: threshold inclusivity test >= t
                    boolean predicted = r.similarityRate >= t;

                    if (isPlag && predicted) tp++;
                    else if (!isPlag && !predicted) tn++;
                    else if (!isPlag && predicted) fp++;
                    else fn++;
                }

                int total = tp + tn + fp + fn;
                double acc = (double) (tp + tn) / total;
                double prec = (tp + fp) == 0 ? 0.0 : (double) tp / (tp + fp);
                double rec = (tp + fn) == 0 ? 0.0 : (double) tp / (tp + fn);
                double spec = (tn + fp) == 0 ? 0.0 : (double) tn / (tn + fp);
                double f1 = (prec + rec) == 0.0 ? 0.0 : (2.0 * prec * rec) / (prec + rec);
                double fpr = (fp + tn) == 0 ? 0.0 : (double) fp / (fp + tn);
                double fnr = (fn + tp) == 0 ? 0.0 : (double) fn / (fn + tp);

                pw.printf("%.2f,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        t, tp, tn, fp, fn, acc, prec, rec, f1, spec, fpr, fnr);

                System.out.printf("Threshold %5.1f%% => TP=%3d, TN=%3d, FP=%3d, FN=%2d | Acc=%.4f, Prec=%.4f, Rec=%.4f, F1=%.4f, Spec=%.4f%n",
                        t, tp, tn, fp, fn, acc, prec, rec, f1, spec);
            }
        }
        System.out.printf("Exported: %s%n", outCsv);
    }

    private void verifyReproducibilityRun2() throws Exception {
        System.out.println("Executing full pipeline reproduction into: " + run2Dir);

        Path pairsCsv = datasetDir.resolve("ground-truth").resolve("pairs.csv");
        List<String> lines = Files.readAllLines(pairsCsv, StandardCharsets.UTF_8);

        Map<String, Set<Long>> cache = new HashMap<>();
        List<PairResultLine> run2Results = new ArrayList<>();

        for (int i = 1; i < lines.size(); i++) {
            String line = lines.get(i).trim();
            if (line.isEmpty()) continue;
            String[] parts = line.split(",");
            String pairId = parts[0];
            String progA = parts[1];
            String progB = parts[2];
            String cat = parts[3];
            String trans = parts[4];
            String label = parts[5];

            Set<Long> fpA = getOrCompute(progA, cache);
            Set<Long> fpB = getOrCompute(progB, cache);

            SimilarityResult sim = calculator.calculate(fpA, fpB);
            boolean isPlag = "PLAGIARISM".equalsIgnoreCase(label);
            boolean predFlag = sim.isFlagged();

            String outcome;
            if (isPlag && predFlag) outcome = "TP";
            else if (!isPlag && !predFlag) outcome = "TN";
            else if (!isPlag && predFlag) outcome = "FP";
            else outcome = "FN";

            run2Results.add(new PairResultLine(
                    pairId, progA, progB, cat, trans, label,
                    fpA.size(), fpB.size(), sim.getSharedHashesCount(),
                    sim.getSimilarityRate().doubleValue(), predFlag, outcome));
        }

        // Export run-2 baseline_pair_results.csv
        Path run2PairCsv = run2Dir.resolve("baseline_pair_results.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(run2PairCsv, StandardCharsets.UTF_8))) {
            pw.println("pair_id,program_a,program_b,category,transformation,ground_truth,hashes_a,hashes_b,shared_hashes,similarity_rate,predicted_flag,outcome");
            for (PairResultLine r : run2Results) {
                pw.printf("%s,%s,%s,%s,%s,%s,%d,%d,%d,%.2f,%b,%s%n",
                        r.pairId, r.progA, r.progB, r.category, r.trans, r.groundTruth,
                        r.hashesA, r.hashesB, r.sharedHashes, r.similarityRate, r.predictedFlag, r.outcome);
            }
        }

        // File comparison with baseline-v1
        Path origPairCsv = baselineResultsDir.resolve("baseline_pair_results.csv");
        byte[] origBytes = Files.readAllBytes(origPairCsv);
        byte[] run2Bytes = Files.readAllBytes(run2PairCsv);

        boolean bitIdentical = Arrays.equals(origBytes, run2Bytes);
        System.out.printf("Bit-for-bit identical with baseline-v1 pair results: %b%n", bitIdentical);

        if (!bitIdentical) {
            List<String> origLines = Files.readAllLines(origPairCsv, StandardCharsets.UTF_8);
            List<String> r2Lines = Files.readAllLines(run2PairCsv, StandardCharsets.UTF_8);
            int diffCount = 0;
            for (int j = 0; j < Math.max(origLines.size(), r2Lines.size()); j++) {
                String ol = j < origLines.size() ? origLines.get(j) : "<EOF>";
                String rl = j < r2Lines.size() ? r2Lines.get(j) : "<EOF>";
                if (!ol.equals(rl)) {
                    diffCount++;
                    if (diffCount <= 5) {
                        System.err.printf("Diff at line %d:%n  Orig: %s%n  Run2: %s%n", j + 1, ol, rl);
                    }
                }
            }
            System.err.printf("Total line diffs: %d%n", diffCount);
        } else {
            System.out.println("[REPRODUCIBILITY CONFIRMED] Output files are 100% deterministic (0 differences across all 535 pairs).");
        }
    }

    private Set<Long> getOrCompute(String name, Map<String, Set<Long>> cache) throws IOException {
        if (cache.containsKey(name)) return cache.get(name);
        Path p = name.startsWith("Base") ?
                datasetDir.resolve("programs").resolve("base").resolve(name) :
                datasetDir.resolve("programs").resolve("generated").resolve(name);

        String code = Files.readString(p, StandardCharsets.UTF_8);
        CompilationUnit cu = parser.parse(code);
        List<AstNode> seq = normalizer.normalize(cu);
        List<KGram> kgrams = kGramGenerator.generateKGrams(seq);
        List<HashedKGram> hashed = new ArrayList<>();
        for (int i = 0; i < kgrams.size(); i++) {
            hashed.add(new HashedKGram(hasher.hash(kgrams.get(i).getConcatenatedTypeString()), i,
                    kgrams.get(i).getRepresentativeNodeType(), kgrams.get(i).getStartLine(), kgrams.get(i).getEndLine()));
        }
        List<HashedKGram> winnowed = winnower.winnow(hashed);
        Set<Long> set = winnowed.stream().map(HashedKGram::getHash).collect(Collectors.toSet());
        cache.put(name, set);
        return set;
    }

    private record ValidatedPair(String pairId, String progA, String progB, String cat, String trans,
                                 String label, boolean filesExist, boolean isDistinct) {}

    private record PairResultLine(String pairId, String progA, String progB, String category, String trans,
                                  String groundTruth, int hashesA, int hashesB, int sharedHashes,
                                  double similarityRate, boolean predictedFlag, String outcome) {}
}
