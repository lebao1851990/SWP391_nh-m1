package com.codeassess.plagiarism.ted;

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
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.io.FileWriter;
import java.io.PrintWriter;
import java.lang.management.ManagementFactory;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IEEE Benchmark Harness: AST Winnowing vs Zhang-Shasha Tree Edit Distance.
 * Includes repeated runtime runs (Mean/Median/StdDev), thread heap allocation tracking,
 * AST size scaling analysis, and strict Dev-tuned Hybrid Pipeline evaluation.
 */
public class PlagiarismBenchmarkTest {

    private JavaAstParser parser;
    private AstNormalizer normalizer;
    private KGramGenerator kGramGen;
    private FingerprintHasher hasher;
    private Winnower winnower;
    private JaccardSimilarityCalculator jaccardCalc;
    private ZhangShashaTed tedCalc;
    private com.sun.management.ThreadMXBean threadBean;

    @BeforeEach
    void setUp() {
        parser = new JavaAstParser();
        normalizer = new AstNormalizer();
        kGramGen = new KGramGenerator(5);
        hasher = new FingerprintHasher();
        winnower = new Winnower(4);
        jaccardCalc = new JaccardSimilarityCalculator();
        tedCalc = new ZhangShashaTed();
        try {
            threadBean = (com.sun.management.ThreadMXBean) ManagementFactory.getThreadMXBean();
        } catch (Exception e) {
            threadBean = null;
        }
    }

    public record BenchmarkPair(
            String id,
            String partition, // "DEV" or "TEST"
            String problemFamily,
            String category, // "Cat-A", "Cat-B", "Cat-C", "Cat-D", "Cat-E"
            int groundTruthLabel, // 1 = Plagiarism, 0 = Non-Plagiarism
            String description,
            String codeA,
            String codeB
    ) {}

    public record BenchmarkResultRow(
            String pairId,
            String partition,
            String problemFamily,
            String category,
            int groundTruth,
            int nodeCountA,
            int nodeCountB,
            double winnowingSim,
            double winnowingMeanMs,
            double winnowingMedianMs,
            long winnowingAllocBytes,
            double tedDistance,
            double tedSimSymmetric,
            double tedSimMax,
            double tedMeanMs,
            double tedMedianMs,
            long tedAllocBytes
    ) {}

    private Set<Long> getWinnowingFingerprints(String code) {
        CompilationUnit cu = parser.parse(code);
        List<AstNode> nodes = normalizer.normalize(cu);
        List<KGram> kgrams = kGramGen.generateKGrams(nodes);
        List<HashedKGram> hashed = new ArrayList<>();
        for (int i = 0; i < kgrams.size(); i++) {
            KGram kg = kgrams.get(i);
            long hashValue = hasher.hash(kg.getConcatenatedTypeString());
            hashed.add(new HashedKGram(hashValue, i, kg.getRepresentativeNodeType(), kg.getStartLine(), kg.getEndLine()));
        }
        List<HashedKGram> winnowed = winnower.winnow(hashed);
        return winnowed.stream().map(HashedKGram::getHash).collect(Collectors.toSet());
    }

    private BenchmarkResultRow evaluatePairWithRepeats(BenchmarkPair pair, int repeatCount) {
        long threadId = Thread.currentThread().getId();

        // 1. Measure Winnowing memory allocation
        long allocBeforeW = (threadBean != null) ? threadBean.getThreadAllocatedBytes(threadId) : 0;
        Set<Long> fpA = getWinnowingFingerprints(pair.codeA());
        Set<Long> fpB = getWinnowingFingerprints(pair.codeB());
        SimilarityResult simRes = jaccardCalc.calculate(fpA, fpB);
        long allocAfterW = (threadBean != null) ? threadBean.getThreadAllocatedBytes(threadId) : 0;
        long winnowingAllocBytes = Math.max(0, allocAfterW - allocBeforeW);
        double winnowingSim = simRes.getSimilarityRate().doubleValue();

        // Repeated runtime measurements for Winnowing
        double[] winTimes = new double[repeatCount];
        for (int i = 0; i < repeatCount; i++) {
            long st = System.nanoTime();
            Set<Long> a = getWinnowingFingerprints(pair.codeA());
            Set<Long> b = getWinnowingFingerprints(pair.codeB());
            jaccardCalc.calculate(a, b);
            winTimes[i] = (System.nanoTime() - st) / 1_000_000.0;
        }
        Arrays.sort(winTimes);
        double winMean = Arrays.stream(winTimes).average().orElse(0);
        double winMedian = winTimes[repeatCount / 2];

        // 2. Measure TED memory allocation
        long allocBeforeT = (threadBean != null) ? threadBean.getThreadAllocatedBytes(threadId) : 0;
        CompilationUnit cuA = parser.parse(pair.codeA());
        CompilationUnit cuB = parser.parse(pair.codeB());
        TedTree treeA = TedTree.fromCompilationUnit(cuA);
        TedTree treeB = TedTree.fromCompilationUnit(cuB);
        ZhangShashaTed.TedResult tedRes = tedCalc.computeDistance(treeA, treeB);
        long allocAfterT = (threadBean != null) ? threadBean.getThreadAllocatedBytes(threadId) : 0;
        long tedAllocBytes = Math.max(0, allocAfterT - allocBeforeT);

        // Repeated runtime measurements for TED
        double[] tedTimes = new double[repeatCount];
        for (int i = 0; i < repeatCount; i++) {
            long st = System.nanoTime();
            CompilationUnit pcuA = parser.parse(pair.codeA());
            CompilationUnit pcuB = parser.parse(pair.codeB());
            TedTree ptA = TedTree.fromCompilationUnit(pcuA);
            TedTree ptB = TedTree.fromCompilationUnit(pcuB);
            tedCalc.computeDistance(ptA, ptB);
            tedTimes[i] = (System.nanoTime() - st) / 1_000_000.0;
        }
        Arrays.sort(tedTimes);
        double tedMean = Arrays.stream(tedTimes).average().orElse(0);
        double tedMedian = tedTimes[repeatCount / 2];

        return new BenchmarkResultRow(
                pair.id(),
                pair.partition(),
                pair.problemFamily(),
                pair.category(),
                pair.groundTruthLabel(),
                treeA.size(),
                treeB.size(),
                winnowingSim,
                winMean,
                winMedian,
                winnowingAllocBytes,
                tedRes.getDistance(),
                tedRes.getSimilaritySymmetric(),
                tedRes.getSimilarityMax(),
                tedMean,
                tedMedian,
                tedAllocBytes
        );
    }

    @Test
    @DisplayName("Execute Full Rigorous IEEE Benchmark with Repeated Measurements")
    void executeFullBenchmark() throws Exception {
        List<BenchmarkPair> pairs = buildBenchmarkDataset();

        // Warm up JVM thoroughly (20 iterations)
        for (int i = 0; i < 20; i++) {
            evaluatePairWithRepeats(pairs.get(0), 1);
        }

        // Run benchmark with 10 repeated timed iterations per pair
        List<BenchmarkResultRow> results = new ArrayList<>();
        for (BenchmarkPair p : pairs) {
            results.add(evaluatePairWithRepeats(p, 10));
        }

        // Export raw results to CSV
        String csvArtifactPath = "C:\\Users\\Admin\\.gemini\\antigravity\\brain\\5c15bb48-bec2-42a5-9dd7-eb73c43b75ac\\task_26_benchmark_raw_results.csv";
        try (PrintWriter writer = new PrintWriter(new FileWriter(csvArtifactPath))) {
            writer.println("pair_id,partition,problem_family,category,ground_truth,nodes_a,nodes_b,winnowing_sim_pct,winnowing_mean_ms,winnowing_median_ms,winnowing_alloc_bytes,ted_distance,ted_sim_symmetric_pct,ted_sim_max_pct,ted_mean_ms,ted_median_ms,ted_alloc_bytes");
            for (BenchmarkResultRow r : results) {
                writer.printf(Locale.US, "%s,%s,%s,%s,%d,%d,%d,%.2f,%.3f,%.3f,%d,%.1f,%.2f,%.2f,%.3f,%.3f,%d%n",
                        r.pairId(), r.partition(), r.problemFamily(), r.category(), r.groundTruth(),
                        r.nodeCountA(), r.nodeCountB(), r.winnowingSim(), r.winnowingMeanMs(), r.winnowingMedianMs(), r.winnowingAllocBytes(),
                        r.tedDistance(), r.tedSimSymmetric(), r.tedSimMax(), r.tedMeanMs(), r.tedMedianMs(), r.tedAllocBytes());
            }
        }
        System.out.println("Exported raw benchmark CSV to: " + csvArtifactPath);

        // Partition into Dev and Test
        List<BenchmarkResultRow> devResults = results.stream().filter(r -> "DEV".equals(r.partition())).toList();
        List<BenchmarkResultRow> testResults = results.stream().filter(r -> "TEST".equals(r.partition())).toList();

        System.out.println("\n=======================================================");
        System.out.println("=== 1. MEMORY & RUNTIME PROFILE (MEASURED EMPIRICALLY) ===");
        System.out.println("=======================================================");
        printMemoryAndRuntimeProfile(results);

        System.out.println("\n=======================================================");
        System.out.println("=== 2. INPUT SIZE SCALABILITY (AST NODES VS LATENCY) ===");
        System.out.println("=======================================================");
        printScalabilityProfile(results);

        System.out.println("\n=======================================================");
        System.out.println("=== 3. CALIBRATION SET (DEV) THRESHOLD TUNING ===");
        System.out.println("=======================================================");
        tuneThresholds(devResults);

        System.out.println("\n=======================================================");
        System.out.println("=== 4. HYBRID PIPELINE CALIBRATION & BLIND EVALUATION ===");
        System.out.println("=======================================================");
        evaluateHybridPipeline(devResults, testResults);

        System.out.println("\n=======================================================");
        System.out.println("=== 5. HELD-OUT EVALUATION SET (TEST) BLIND EVALUATION ===");
        System.out.println("=======================================================");
        evaluateHeldOut(testResults, 70.0, 70.0);

        System.out.println("\n=======================================================");
        System.out.println("=== 6. CATEGORY BREAKDOWN (TEST SET) ===");
        System.out.println("=======================================================");
        printCategoryBreakdown(testResults, 70.0, 70.0);

        assertTrue(Files.exists(Paths.get(csvArtifactPath)));
    }

    private void printMemoryAndRuntimeProfile(List<BenchmarkResultRow> rows) {
        double avgWinMeanMs = rows.stream().mapToDouble(BenchmarkResultRow::winnowingMeanMs).average().orElse(0);
        double avgWinMedianMs = rows.stream().mapToDouble(BenchmarkResultRow::winnowingMedianMs).average().orElse(0);
        double maxWinMs = rows.stream().mapToDouble(BenchmarkResultRow::winnowingMeanMs).max().orElse(0);
        double avgWinAllocKb = rows.stream().mapToLong(BenchmarkResultRow::winnowingAllocBytes).average().orElse(0) / 1024.0;
        double maxWinAllocKb = rows.stream().mapToLong(BenchmarkResultRow::winnowingAllocBytes).max().orElse(0) / 1024.0;

        double avgTedMeanMs = rows.stream().mapToDouble(BenchmarkResultRow::tedMeanMs).average().orElse(0);
        double avgTedMedianMs = rows.stream().mapToDouble(BenchmarkResultRow::tedMedianMs).average().orElse(0);
        double maxTedMs = rows.stream().mapToDouble(BenchmarkResultRow::tedMeanMs).max().orElse(0);
        double avgTedAllocKb = rows.stream().mapToLong(BenchmarkResultRow::tedAllocBytes).average().orElse(0) / 1024.0;
        double maxTedAllocKb = rows.stream().mapToLong(BenchmarkResultRow::tedAllocBytes).max().orElse(0) / 1024.0;

        System.out.printf(Locale.US, "WINNOWING: Latency Mean=%.3f ms, Median=%.3f ms, Max=%.3f ms | Memory Mean=%.1f KB, Peak=%.1f KB%n",
                avgWinMeanMs, avgWinMedianMs, maxWinMs, avgWinAllocKb, maxWinAllocKb);
        System.out.printf(Locale.US, "TED (Z-S): Latency Mean=%.3f ms, Median=%.3f ms, Max=%.3f ms | Memory Mean=%.1f KB, Peak=%.1f KB%n",
                avgTedMeanMs, avgTedMedianMs, maxTedMs, avgTedAllocKb, maxTedAllocKb);
        System.out.printf(Locale.US, "RATIOS (TED / Winnowing): Latency Factor=%.2fx, Memory Factor=%.2fx%n",
                avgTedMeanMs / Math.max(0.001, avgWinMeanMs), avgTedAllocKb / Math.max(0.1, avgWinAllocKb));
    }

    private void printScalabilityProfile(List<BenchmarkResultRow> rows) {
        record Bin(String label, int minNodes, int maxNodes) {}
        List<Bin> bins = List.of(
                new Bin("Small (<70 nodes)", 0, 69),
                new Bin("Medium (70-100 nodes)", 70, 100),
                new Bin("Large (101-150 nodes)", 101, 150),
                new Bin("X-Large (>150 nodes)", 151, 1000)
        );

        System.out.printf(Locale.US, "%-24s | %-6s | %-16s | %-16s | %-14s | %-14s%n",
                "AST Size Bin", "Pairs", "Win Mean (ms)", "TED Mean (ms)", "Win Mem (KB)", "TED Mem (KB)");
        System.out.println("------------------------------------------------------------------------------------------------------");

        for (Bin bin : bins) {
            List<BenchmarkResultRow> inBin = rows.stream()
                    .filter(r -> {
                        int avgNodes = (r.nodeCountA() + r.nodeCountB()) / 2;
                        return avgNodes >= bin.minNodes && avgNodes <= bin.maxNodes;
                    }).toList();

            if (inBin.isEmpty()) continue;

            double winMs = inBin.stream().mapToDouble(BenchmarkResultRow::winnowingMeanMs).average().orElse(0);
            double tedMs = inBin.stream().mapToDouble(BenchmarkResultRow::tedMeanMs).average().orElse(0);
            double winMem = inBin.stream().mapToLong(BenchmarkResultRow::winnowingAllocBytes).average().orElse(0) / 1024.0;
            double tedMem = inBin.stream().mapToLong(BenchmarkResultRow::tedAllocBytes).average().orElse(0) / 1024.0;

            System.out.printf(Locale.US, "%-24s | %6d | %16.3f | %16.3f | %14.1f | %14.1f%n",
                    bin.label, inBin.size(), winMs, tedMs, winMem, tedMem);
        }
    }

    private void tuneThresholds(List<BenchmarkResultRow> devRows) {
        System.out.println("Threshold Sweep for Single-Engine F1 Optimization on Calibration Set:");
        System.out.printf(Locale.US, "%-10s | %-16s | %-16s | %-16s%n", "Threshold", "Winnowing F1", "TED-Sym F1", "TED-Max F1");
        System.out.println("------------------------------------------------------------------");

        double bestWinThresh = 70.0, bestTedSymThresh = 70.0, bestTedMaxThresh = 70.0;
        double maxWinF1 = -1, maxTedSymF1 = -1, maxTedMaxF1 = -1;

        for (int t = 50; t <= 90; t += 5) {
            double thresh = t;
            double winF1 = computeMetrics(devRows, r -> r.winnowingSim() >= thresh).f1;
            double tedSymF1 = computeMetrics(devRows, r -> r.tedSimSymmetric() >= thresh).f1;
            double tedMaxF1 = computeMetrics(devRows, r -> r.tedSimMax() >= thresh).f1;

            System.out.printf(Locale.US, "%8.1f%%  | %14.3f | %14.3f | %14.3f%n", thresh, winF1, tedSymF1, tedMaxF1);

            if (winF1 > maxWinF1) { maxWinF1 = winF1; bestWinThresh = thresh; }
            if (tedSymF1 > maxTedSymF1) { maxTedSymF1 = tedSymF1; bestTedSymThresh = thresh; }
            if (tedMaxF1 > maxTedMaxF1) { maxTedMaxF1 = tedMaxF1; bestTedMaxThresh = thresh; }
        }

        System.out.printf(Locale.US, "\nOptimal Single Thresholds -> Winnowing: %.1f%% (F1=%.3f), TED-Sym: %.1f%% (F1=%.3f), TED-Max: %.1f%% (F1=%.3f)%n",
                bestWinThresh, maxWinF1, bestTedSymThresh, maxTedSymF1, bestTedMaxThresh, maxTedMaxF1);
    }

    private void evaluateHybridPipeline(List<BenchmarkResultRow> devRows, List<BenchmarkResultRow> testRows) {
        // We evaluate two distinct hybrid configurations tuned strictly on DEV:
        // Configuration 1 (Cascade / Recovery Filter):
        //   Flag if Winnowing >= 70% OR (Winnowing >= theta_filter AND TED-Sym >= theta_ted)
        // Configuration 2 (Strict Gated Filter):
        //   Flag iff Winnowing >= theta_filter AND TED-Sym >= theta_ted

        System.out.println("Tuning Hybrid Configurations strictly on Calibration Set (Dev):");

        double bestFilterThresh = 30.0;
        double bestTedThresh = 75.0;
        double maxDevF1 = -1;

        // Grid search on Dev
        for (double wFilt = 20.0; wFilt <= 50.0; wFilt += 5.0) {
            for (double tThresh = 65.0; tThresh <= 85.0; tThresh += 5.0) {
                final double wf = wFilt, tt = tThresh;
                Metrics mCascade = computeMetrics(devRows, r -> r.winnowingSim() >= 70.0 || (r.winnowingSim() >= wf && r.tedSimSymmetric() >= tt));
                if (mCascade.f1 > maxDevF1) {
                    maxDevF1 = mCascade.f1;
                    bestFilterThresh = wf;
                    bestTedThresh = tt;
                }
            }
        }

        System.out.printf(Locale.US, "Optimal Dev-Tuned Cascade Hybrid: WinFilter=%.1f%%, TED-Sym=%.1f%% -> Dev F1=%.3f%n",
                bestFilterThresh, bestTedThresh, maxDevF1);

        // Evaluate Frozen Hybrid on Held-Out Test Set
        final double finalWf = bestFilterThresh;
        final double finalTt = bestTedThresh;

        Metrics devCascade = computeMetrics(devRows, r -> r.winnowingSim() >= 70.0 || (r.winnowingSim() >= finalWf && r.tedSimSymmetric() >= finalTt));
        Metrics testCascade = computeMetrics(testRows, r -> r.winnowingSim() >= 70.0 || (r.winnowingSim() >= finalWf && r.tedSimSymmetric() >= finalTt));

        // Also evaluate Strict Gated (Win >= 30% AND TED >= 75%)
        Metrics testStrict = computeMetrics(testRows, r -> r.winnowingSim() >= finalWf && r.tedSimSymmetric() >= finalTt);

        System.out.println("\n--- FROZEN HYBRID PERFORMANCE ON HELD-OUT TEST DATA (25 Pairs) ---");
        System.out.printf(Locale.US, "%-22s | %-16s | %-16s%n", "Metric", "Cascade Hybrid", "Strict Gated Hybrid");
        System.out.println("------------------------------------------------------------------");
        System.out.printf(Locale.US, "%-22s | %16d | %16d%n", "TP", testCascade.tp, testStrict.tp);
        System.out.printf(Locale.US, "%-22s | %16d | %16d%n", "FP", testCascade.fp, testStrict.fp);
        System.out.printf(Locale.US, "%-22s | %16d | %16d%n", "TN", testCascade.tn, testStrict.tn);
        System.out.printf(Locale.US, "%-22s | %16d | %16d%n", "FN", testCascade.fn, testStrict.fn);
        System.out.printf(Locale.US, "%-22s | %15.2f%% | %15.2f%%%n", "Precision", testCascade.precision * 100, testStrict.precision * 100);
        System.out.printf(Locale.US, "%-22s | %15.2f%% | %15.2f%%%n", "Recall", testCascade.recall * 100, testStrict.recall * 100);
        System.out.printf(Locale.US, "%-22s | %15.2f%% | %15.2f%%%n", "F1-Score", testCascade.f1 * 100, testStrict.f1 * 100);
        System.out.printf(Locale.US, "%-22s | %15.2f%% | %15.2f%%%n", "Accuracy", testCascade.accuracy * 100, testStrict.accuracy * 100);
        System.out.printf(Locale.US, "%-22s | %15.2f%% | %15.2f%%%n", "False Positive Rate", testCascade.fpr * 100, testStrict.fpr * 100);
        System.out.printf(Locale.US, "%-22s | %15.2f%% | %15.2f%%%n", "False Negative Rate", testCascade.fnr * 100, testStrict.fnr * 100);
    }

    private void evaluateHeldOut(List<BenchmarkResultRow> testRows, double winThresh, double tedThresh) {
        Metrics winM = computeMetrics(testRows, r -> r.winnowingSim() >= winThresh);
        Metrics tedM = computeMetrics(testRows, r -> r.tedSimSymmetric() >= tedThresh);

        System.out.printf("HELD-OUT EVALUATION (25 Pairs: 20 Positives, 5 Negatives):%n");
        System.out.printf(Locale.US, "%-18s | %-16s | %-16s%n", "Metric", "Winnowing (70%)", "TED-Sym (70%)");
        System.out.println("------------------------------------------------------");
        System.out.printf(Locale.US, "%-18s | %16d | %16d%n", "TP", winM.tp, tedM.tp);
        System.out.printf(Locale.US, "%-18s | %16d | %16d%n", "FP", winM.fp, tedM.fp);
        System.out.printf(Locale.US, "%-18s | %16d | %16d%n", "TN", winM.tn, tedM.tn);
        System.out.printf(Locale.US, "%-18s | %16d | %16d%n", "FN", winM.fn, tedM.fn);
        System.out.printf(Locale.US, "%-18s | %15.2f%% | %15.2f%%%n", "Precision", winM.precision * 100, tedM.precision * 100);
        System.out.printf(Locale.US, "%-18s | %15.2f%% | %15.2f%%%n", "Recall", winM.recall * 100, tedM.recall * 100);
        System.out.printf(Locale.US, "%-18s | %15.2f%% | %15.2f%%%n", "F1-Score", winM.f1 * 100, tedM.f1 * 100);
        System.out.printf(Locale.US, "%-18s | %15.2f%% | %15.2f%%%n", "Accuracy", winM.accuracy * 100, tedM.accuracy * 100);
        System.out.printf(Locale.US, "%-18s | %15.2f%% | %15.2f%%%n", "FPR", winM.fpr * 100, tedM.fpr * 100);
        System.out.printf(Locale.US, "%-18s | %15.2f%% | %15.2f%%%n", "FNR", winM.fnr * 100, tedM.fnr * 100);
    }

    private void printCategoryBreakdown(List<BenchmarkResultRow> testRows, double winThresh, double tedThresh) {
        String[] cats = {"Cat-A", "Cat-B", "Cat-C", "Cat-D", "Cat-E"};
        String[] catNames = {
                "Cat-A (Identical)",
                "Cat-B (Conventional)",
                "Cat-C (Structural)",
                "Cat-D (AI-Assisted)",
                "Cat-E (True Negative)"
        };

        System.out.printf(Locale.US, "%-24s | %-6s | %-16s | %-16s%n", "Category", "Pairs", "Winnowing Recall", "TED-Sym Recall");
        System.out.println("-----------------------------------------------------------------------");

        for (int i = 0; i < cats.length; i++) {
            String cat = cats[i];
            List<BenchmarkResultRow> catRows = testRows.stream().filter(r -> cat.equals(r.category())).toList();
            if (cat.equals("Cat-E")) {
                long winTN = catRows.stream().filter(r -> r.winnowingSim() < winThresh).count();
                long tedTN = catRows.stream().filter(r -> r.tedSimSymmetric() < tedThresh).count();
                double winTNR = (double) winTN / catRows.size() * 100.0;
                double tedTNR = (double) tedTN / catRows.size() * 100.0;
                System.out.printf(Locale.US, "%-24s | %6d | %14.1f%% (TN) | %14.1f%% (TN)%n", catNames[i], catRows.size(), winTNR, tedTNR);
            } else {
                long winTP = catRows.stream().filter(r -> r.winnowingSim() >= winThresh).count();
                long tedTP = catRows.stream().filter(r -> r.tedSimSymmetric() >= tedThresh).count();
                double winRec = (double) winTP / catRows.size() * 100.0;
                double tedRec = (double) tedTP / catRows.size() * 100.0;
                System.out.printf(Locale.US, "%-24s | %6d | %15.1f%% | %15.1f%%%n", catNames[i], catRows.size(), winRec, tedRec);
            }
        }
    }

    record Metrics(int tp, int fp, int tn, int fn, double precision, double recall, double f1, double accuracy, double fpr, double fnr) {}

    private Metrics computeMetrics(List<BenchmarkResultRow> rows, java.util.function.Predicate<BenchmarkResultRow> isFlagged) {
        int tp = 0, fp = 0, tn = 0, fn = 0;
        for (BenchmarkResultRow r : rows) {
            boolean flagged = isFlagged.test(r);
            if (r.groundTruth() == 1) {
                if (flagged) tp++;
                else fn++;
            } else {
                if (flagged) fp++;
                else tn++;
            }
        }
        double precision = (tp + fp > 0) ? (double) tp / (tp + fp) : 1.0;
        double recall = (tp + fn > 0) ? (double) tp / (tp + fn) : 0.0;
        double f1 = (precision + recall > 0) ? 2.0 * precision * recall / (precision + recall) : 0.0;
        double accuracy = (rows.size() > 0) ? (double) (tp + tn) / rows.size() : 0.0;
        double fpr = (fp + tn > 0) ? (double) fp / (fp + tn) : 0.0;
        double fnr = (tp + fn > 0) ? (double) fn / (tp + fn) : 0.0;
        return new Metrics(tp, fp, tn, fn, precision, recall, f1, accuracy, fpr, fnr);
    }

    private List<BenchmarkPair> buildBenchmarkDataset() {
        List<BenchmarkPair> list = new ArrayList<>();

        // =========================================================================
        // CALIBRATION SET (DEV) - Problems 1, 2, 3
        // =========================================================================

        // P1: ArrayOps
        String p1Base = """
                public class ArrayOps {
                    public static int sumArray(int[] numbers) {
                        int total = 0;
                        for (int i = 0; i < numbers.length; i++) {
                            total += numbers[i];
                        }
                        return total;
                    }
                    public static int findMax(int[] numbers) {
                        int max = numbers[0];
                        for (int i = 1; i < numbers.length; i++) {
                            if (numbers[i] > max) {
                                max = numbers[i];
                            }
                        }
                        return max;
                    }
                }
                """;
        String p1CatB = """
                // Utility class for array operations
                public class ArrayHelper {
                    // Calculates total sum of items
                    public static int computeSum(int[] values) {
                        int accum = 0;
                        for (int idx = 0; idx < values.length; idx++) {
                            accum += values[idx];
                        }
                        return accum;
                    }
                    // Finds peak value
                    public static int locatePeak(int[] data) {
                        int peakVal = data[0];
                        for (int k = 1; k < data.length; k++) {
                            if (data[k] > peakVal) {
                                peakVal = data[k];
                            }
                        }
                        return peakVal;
                    }
                }
                """;
        String p1CatC = """
                public class ArrayOpsWhile {
                    public static int sumArray(int[] numbers) {
                        int total = 0;
                        int i = 0;
                        while (i < numbers.length) {
                            total += numbers[i];
                            i++;
                        }
                        return total;
                    }
                    public static int findMax(int[] numbers) {
                        int max = numbers[0];
                        int i = 1;
                        while (i < numbers.length) {
                            if (numbers[i] > max) {
                                max = numbers[i];
                            }
                            i++;
                        }
                        return max;
                    }
                }
                """;
        String p1CatD = """
                import java.util.Arrays;
                public class ArrayOpsStream {
                    public static int sumArray(int[] numbers) {
                        return Arrays.stream(numbers).sum();
                    }
                    public static int findMax(int[] numbers) {
                        return Arrays.stream(numbers).max().orElse(numbers[0]);
                    }
                }
                """;
        String p1CatE = """
                public class PalindromeChecker {
                    public static boolean isPalindrome(String s) {
                        int left = 0;
                        int right = s.length() - 1;
                        while (left < right) {
                            if (s.charAt(left) != s.charAt(right)) {
                                return false;
                            }
                            left++;
                            right--;
                        }
                        return true;
                    }
                }
                """;

        list.add(new BenchmarkPair("DEV-P1-A", "DEV", "Prob-1-ArrayOps", "Cat-A", 1, "Identical ArrayOps", p1Base, p1Base));
        list.add(new BenchmarkPair("DEV-P1-B", "DEV", "Prob-1-ArrayOps", "Cat-B", 1, "Renamed ArrayOps", p1Base, p1CatB));
        list.add(new BenchmarkPair("DEV-P1-C", "DEV", "Prob-1-ArrayOps", "Cat-C", 1, "For-to-While ArrayOps", p1Base, p1CatC));
        list.add(new BenchmarkPair("DEV-P1-D", "DEV", "Prob-1-ArrayOps", "Cat-D", 1, "Stream API ArrayOps", p1Base, p1CatD));
        list.add(new BenchmarkPair("DEV-P1-E", "DEV", "Prob-1-ArrayOps", "Cat-E", 0, "ArrayOps vs Palindrome", p1Base, p1CatE));

        // P2: BinarySearch
        String p2Base = """
                public class SearchService {
                    public static int binarySearch(int[] arr, int target) {
                        int low = 0;
                        int high = arr.length - 1;
                        while (low <= high) {
                            int mid = (low + high) / 2;
                            if (arr[mid] == target) {
                                return mid;
                            } else if (arr[mid] < target) {
                                low = mid + 1;
                            } else {
                                high = mid - 1;
                            }
                        }
                        return -1;
                    }
                }
                """;
        String p2CatB = """
                public class SearchHelper {
                    public static int locateElement(int[] dataset, int needle) {
                        int startIndex = 0;
                        int endIndex = dataset.length - 1;
                        while (startIndex <= endIndex) {
                            int center = (startIndex + endIndex) / 2;
                            if (dataset[center] == needle) {
                                return center;
                            } else if (dataset[center] < needle) {
                                startIndex = center + 1;
                            } else {
                                endIndex = center - 1;
                            }
                        }
                        return -1;
                    }
                }
                """;
        String p2CatC = """
                public class RecursiveSearch {
                    public static int binarySearch(int[] arr, int target) {
                        return searchHelper(arr, target, 0, arr.length - 1);
                    }
                    private static int searchHelper(int[] arr, int target, int low, int high) {
                        if (low > high) return -1;
                        int mid = (low + high) / 2;
                        if (arr[mid] == target) return mid;
                        if (arr[mid] < target) return searchHelper(arr, target, mid + 1, high);
                        return searchHelper(arr, target, low, mid - 1);
                    }
                }
                """;
        String p2CatD = """
                public class ObfuscatedSearch {
                    public static int binarySearch(int[] arr, int target) {
                        int l = 0, r = arr.length - 1;
                        while (l <= r) {
                            int m = (l + r) >>> 1;
                            int val = arr[m];
                            if (val == target) return m;
                            l = (val < target) ? m + 1 : l;
                            r = (val >= target) ? m - 1 : r;
                        }
                        return -1;
                    }
                }
                """;
        String p2CatE = """
                public class LinearSearch {
                    public static int search(int[] arr, int target) {
                        for (int i = 0; i < arr.length; i++) {
                            if (arr[i] == target) return i;
                        }
                        return -1;
                    }
                }
                """;

        list.add(new BenchmarkPair("DEV-P2-A", "DEV", "Prob-2-BinarySearch", "Cat-A", 1, "Identical BinarySearch", p2Base, p2Base));
        list.add(new BenchmarkPair("DEV-P2-B", "DEV", "Prob-2-BinarySearch", "Cat-B", 1, "Renamed BinarySearch", p2Base, p2CatB));
        list.add(new BenchmarkPair("DEV-P2-C", "DEV", "Prob-2-BinarySearch", "Cat-C", 1, "Iterative to Recursive", p2Base, p2CatC));
        list.add(new BenchmarkPair("DEV-P2-D", "DEV", "Prob-2-BinarySearch", "Cat-D", 1, "Bitwise Ternary Search", p2Base, p2CatD));
        list.add(new BenchmarkPair("DEV-P2-E", "DEV", "Prob-2-BinarySearch", "Cat-E", 0, "Binary vs Linear Search", p2Base, p2CatE));

        // P3: BubbleSort
        String p3Base = """
                public class BubbleSort {
                    public static void sort(int[] arr) {
                        int n = arr.length;
                        for (int i = 0; i < n - 1; i++) {
                            for (int j = 0; j < n - i - 1; j++) {
                                if (arr[j] > arr[j + 1]) {
                                    int temp = arr[j];
                                    arr[j] = arr[j + 1];
                                    arr[j + 1] = temp;
                                }
                            }
                        }
                    }
                }
                """;
        String p3CatB = """
                public class SortArray {
                    public static void orderElements(int[] data) {
                        int len = data.length;
                        for (int outer = len - 1; outer > 0; outer--) {
                            for (int inner = 0; inner < outer; inner++) {
                                if (data[inner] > data[inner + 1]) {
                                    int swapVar = data[inner];
                                    data[inner] = data[inner + 1];
                                    data[inner + 1] = swapVar;
                                }
                            }
                        }
                    }
                }
                """;
        String p3CatC = """
                public class BubbleSortWhile {
                    public static void sort(int[] arr) {
                        int n = arr.length;
                        boolean swapped = true;
                        int i = 0;
                        while (i < n - 1 && swapped) {
                            swapped = false;
                            int j = 0;
                            while (j < n - i - 1) {
                                if (arr[j] > arr[j + 1]) {
                                    int temp = arr[j];
                                    arr[j] = arr[j + 1];
                                    arr[j + 1] = temp;
                                    swapped = true;
                                }
                                j++;
                            }
                            i++;
                        }
                    }
                }
                """;
        String p3CatD = """
                public class BubbleSortHelper {
                    public static void sort(int[] arr) {
                        int n = arr.length;
                        for (int i = 0; i < n - 1; i++) {
                            for (int j = 0; j < n - i - 1; j++) {
                                checkAndSwap(arr, j, j + 1);
                            }
                        }
                    }
                    private static void checkAndSwap(int[] arr, int a, int b) {
                        if (arr[a] > arr[b]) {
                            int t = arr[a];
                            arr[a] = arr[b];
                            arr[b] = t;
                        }
                    }
                }
                """;
        String p3CatE = """
                public class InsertionSort {
                    public static void sort(int[] arr) {
                        for (int i = 1; i < arr.length; i++) {
                            int key = arr[i];
                            int j = i - 1;
                            while (j >= 0 && arr[j] > key) {
                                arr[j + 1] = arr[j];
                                j--;
                            }
                            arr[j + 1] = key;
                        }
                    }
                }
                """;

        list.add(new BenchmarkPair("DEV-P3-A", "DEV", "Prob-3-BubbleSort", "Cat-A", 1, "Identical BubbleSort", p3Base, p3Base));
        list.add(new BenchmarkPair("DEV-P3-B", "DEV", "Prob-3-BubbleSort", "Cat-B", 1, "Renamed & Inverted BubbleSort", p3Base, p3CatB));
        list.add(new BenchmarkPair("DEV-P3-C", "DEV", "Prob-3-BubbleSort", "Cat-C", 1, "Nested While Early Exit", p3Base, p3CatC));
        list.add(new BenchmarkPair("DEV-P3-D", "DEV", "Prob-3-BubbleSort", "Cat-D", 1, "Extracted Swap Method", p3Base, p3CatD));
        list.add(new BenchmarkPair("DEV-P3-E", "DEV", "Prob-3-BubbleSort", "Cat-E", 0, "Bubble vs Insertion Sort", p3Base, p3CatE));

        // =========================================================================
        // HELD-OUT EVALUATION SET (TEST) - Problems 4, 5, 6, 7, 8
        // =========================================================================

        // P4: PalindromeValidator
        String p4Base = """
                public class PalindromeService {
                    public static boolean validate(String text) {
                        int left = 0;
                        int right = text.length() - 1;
                        while (left < right) {
                            if (text.charAt(left) != text.charAt(right)) {
                                return false;
                            }
                            left++;
                            right--;
                        }
                        return true;
                    }
                }
                """;
        String p4CatB = """
                public class PalindromeChecker {
                    public static boolean checkSymmetry(String input) {
                        int startIdx = 0;
                        int endIdx = input.length() - 1;
                        while (startIdx < endIdx) {
                            if (input.charAt(startIdx) != input.charAt(endIdx)) {
                                return false;
                            }
                            startIdx++;
                            endIdx--;
                        }
                        return true;
                    }
                }
                """;
        String p4CatC = """
                public class PalindromeReverse {
                    public static boolean validate(String text) {
                        StringBuilder sb = new StringBuilder(text);
                        String reversed = sb.reverse().toString();
                        return text.equals(reversed);
                    }
                }
                """;
        String p4CatD = """
                import java.util.stream.IntStream;
                public class PalindromeStream {
                    public static boolean validate(String text) {
                        int len = text.length();
                        return IntStream.range(0, len / 2)
                                .allMatch(i -> text.charAt(i) == text.charAt(len - 1 - i));
                    }
                }
                """;
        String p4CatE = """
                public class AnagramValidator {
                    public static boolean isAnagram(String s1, String s2) {
                        if (s1.length() != s2.length()) return false;
                        int[] counts = new int[256];
                        for (int i = 0; i < s1.length(); i++) {
                            counts[s1.charAt(i)]++;
                            counts[s2.charAt(i)]--;
                        }
                        for (int c : counts) {
                            if (c != 0) return false;
                        }
                        return true;
                    }
                }
                """;

        list.add(new BenchmarkPair("TEST-P4-A", "TEST", "Prob-4-Palindrome", "Cat-A", 1, "Identical Palindrome", p4Base, p4Base));
        list.add(new BenchmarkPair("TEST-P4-B", "TEST", "Prob-4-Palindrome", "Cat-B", 1, "Renamed Palindrome", p4Base, p4CatB));
        list.add(new BenchmarkPair("TEST-P4-C", "TEST", "Prob-4-Palindrome", "Cat-C", 1, "Two-Pointer vs StringBuilder.reverse", p4Base, p4CatC));
        list.add(new BenchmarkPair("TEST-P4-D", "TEST", "Prob-4-Palindrome", "Cat-D", 1, "Stream allMatch Palindrome", p4Base, p4CatD));
        list.add(new BenchmarkPair("TEST-P4-E", "TEST", "Prob-4-Palindrome", "Cat-E", 0, "Palindrome vs Anagram", p4Base, p4CatE));

        // P5: FibonacciGenerator
        String p5Base = """
                public class FibonacciService {
                    public static int compute(int n) {
                        if (n <= 1) return n;
                        int a = 0;
                        int b = 1;
                        for (int i = 2; i <= n; i++) {
                            int c = a + b;
                            a = b;
                            b = c;
                        }
                        return b;
                    }
                }
                """;
        String p5CatB = """
                public class FiboCalculator {
                    public static int getTerm(int count) {
                        if (count <= 1) return count;
                        int prevVal = 0;
                        int currVal = 1;
                        for (int step = 2; step <= count; step++) {
                            int nextVal = prevVal + currVal;
                            prevVal = currVal;
                            currVal = nextVal;
                        }
                        return currVal;
                    }
                }
                """;
        String p5CatC = """
                public class FibonacciDpArray {
                    public static int compute(int n) {
                        if (n <= 1) return n;
                        int[] dp = new int[n + 1];
                        dp[0] = 0;
                        dp[1] = 1;
                        for (int i = 2; i <= n; i++) {
                            dp[i] = dp[i - 1] + dp[i - 2];
                        }
                        return dp[n];
                    }
                }
                """;
        String p5CatD = """
                public class FibonacciMemoized {
                    public static int compute(int n) {
                        int[] memo = new int[n + 1];
                        return helper(n, memo);
                    }
                    private static int helper(int n, int[] memo) {
                        if (n <= 1) return n;
                        if (memo[n] != 0) return memo[n];
                        memo[n] = helper(n - 1, memo) + helper(n - 2, memo);
                        return memo[n];
                    }
                }
                """;
        String p5CatE = """
                public class FactorialCalculator {
                    public static int factorial(int n) {
                        int res = 1;
                        for (int i = 2; i <= n; i++) {
                            res *= i;
                        }
                        return res;
                    }
                }
                """;

        list.add(new BenchmarkPair("TEST-P5-A", "TEST", "Prob-5-Fibonacci", "Cat-A", 1, "Identical Fibonacci", p5Base, p5Base));
        list.add(new BenchmarkPair("TEST-P5-B", "TEST", "Prob-5-Fibonacci", "Cat-B", 1, "Renamed Fibonacci", p5Base, p5CatB));
        list.add(new BenchmarkPair("TEST-P5-C", "TEST", "Prob-5-Fibonacci", "Cat-C", 1, "Iterative vs DP Array", p5Base, p5CatC));
        list.add(new BenchmarkPair("TEST-P5-D", "TEST", "Prob-5-Fibonacci", "Cat-D", 1, "Memoized Recursive Fibonacci", p5Base, p5CatD));
        list.add(new BenchmarkPair("TEST-P5-E", "TEST", "Prob-5-Fibonacci", "Cat-E", 0, "Fibonacci vs Factorial", p5Base, p5CatE));

        // P6: PrimeOps
        String p6Base = """
                public class PrimeService {
                    public static boolean isPrime(int n) {
                        if (n <= 1) return false;
                        for (int i = 2; i * i <= n; i++) {
                            if (n % i == 0) return false;
                        }
                        return true;
                    }
                }
                """;
        String p6CatB = """
                public class PrimeChecker {
                    public static boolean testPrime(int value) {
                        if (value < 2) return false;
                        int limit = (int) Math.sqrt(value);
                        for (int factor = 2; factor <= limit; factor++) {
                            if (value % factor == 0) return false;
                        }
                        return true;
                    }
                }
                """;
        String p6CatC = """
                public class PrimeWhileStep {
                    public static boolean isPrime(int n) {
                        if (n <= 1) return false;
                        if (n == 2) return true;
                        if (n % 2 == 0) return false;
                        int d = 3;
                        while (d * d <= n) {
                            if (n % d == 0) return false;
                            d += 2;
                        }
                        return true;
                    }
                }
                """;
        String p6CatD = """
                import java.util.stream.IntStream;
                public class PrimeStream {
                    public static boolean isPrime(int n) {
                        return n > 1 && IntStream.rangeClosed(2, (int) Math.sqrt(n))
                                .noneMatch(d -> n % d == 0);
                    }
                }
                """;
        String p6CatE = """
                public class MathGcd {
                    public static int gcd(int a, int b) {
                        while (b != 0) {
                            int t = b;
                            b = a % b;
                            a = t;
                        }
                        return a;
                    }
                }
                """;

        list.add(new BenchmarkPair("TEST-P6-A", "TEST", "Prob-6-PrimeSieve", "Cat-A", 1, "Identical Prime Check", p6Base, p6Base));
        list.add(new BenchmarkPair("TEST-P6-B", "TEST", "Prob-6-PrimeSieve", "Cat-B", 1, "Renamed Prime Check", p6Base, p6CatB));
        list.add(new BenchmarkPair("TEST-P6-C", "TEST", "Prob-6-PrimeSieve", "Cat-C", 1, "Step-2 While Loop Odd Check", p6Base, p6CatC));
        list.add(new BenchmarkPair("TEST-P6-D", "TEST", "Prob-6-PrimeSieve", "Cat-D", 1, "IntStream noneMatch Sieve", p6Base, p6CatD));
        list.add(new BenchmarkPair("TEST-P6-E", "TEST", "Prob-6-PrimeSieve", "Cat-E", 0, "Prime Check vs GCD", p6Base, p6CatE));

        // P7: MatrixMultiplier
        String p7Base = """
                public class MatrixOps {
                    public static int[][] multiply(int[][] a, int[][] b) {
                        int r1 = a.length, c1 = a[0].length, c2 = b[0].length;
                        int[][] res = new int[r1][c2];
                        for (int i = 0; i < r1; i++) {
                            for (int j = 0; j < c2; j++) {
                                int sum = 0;
                                for (int k = 0; k < c1; k++) {
                                    sum += a[i][k] * b[k][j];
                                }
                                res[i][j] = sum;
                            }
                        }
                        return res;
                    }
                }
                """;
        String p7CatB = """
                public class MatrixEngine {
                    public static int[][] computeProduct(int[][] matA, int[][] matB) {
                        int rows = matA.length;
                        int cols = matB[0].length;
                        int inner = matA[0].length;
                        int[][] product = new int[rows][cols];
                        for (int r = 0; r < rows; r++) {
                            for (int c = 0; c < cols; c++) {
                                int cellAccum = 0;
                                for (int idx = 0; idx < inner; idx++) {
                                    cellAccum += matA[r][idx] * matB[idx][c];
                                }
                                product[r][c] = cellAccum;
                            }
                        }
                        return product;
                    }
                }
                """;
        String p7CatC = """
                public class MatrixFlattened {
                    public static int[] multiplyFlat(int[] a, int[] b, int r1, int c1, int c2) {
                        int[] res = new int[r1 * c2];
                        for (int i = 0; i < r1; i++) {
                            for (int j = 0; j < c2; j++) {
                                int sum = 0;
                                for (int k = 0; k < c1; k++) {
                                    sum += a[i * c1 + k] * b[k * c2 + j];
                                }
                                res[i * c2 + j] = sum;
                            }
                        }
                        return res;
                    }
                }
                """;
        String p7CatD = """
                public class MatrixTransposed {
                    public static int[][] multiply(int[][] a, int[][] b) {
                        int r1 = a.length, c1 = a[0].length, c2 = b[0].length;
                        int[][] bTrans = transpose(b);
                        int[][] res = new int[r1][c2];
                        for (int i = 0; i < r1; i++) {
                            for (int j = 0; j < c2; j++) {
                                res[i][j] = dotProduct(a[i], bTrans[j]);
                            }
                        }
                        return res;
                    }
                    private static int[][] transpose(int[][] m) {
                        int[][] t = new int[m[0].length][m.length];
                        for (int i = 0; i < m.length; i++)
                            for (int j = 0; j < m[0].length; j++)
                                t[j][i] = m[i][j];
                        return t;
                    }
                    private static int dotProduct(int[] u, int[] v) {
                        int s = 0;
                        for (int k = 0; k < u.length; k++) s += u[k] * v[k];
                        return s;
                    }
                }
                """;
        String p7CatE = """
                public class MatrixTranspositionOnly {
                    public static int[][] transpose(int[][] matrix) {
                        int r = matrix.length;
                        int c = matrix[0].length;
                        int[][] t = new int[c][r];
                        for (int i = 0; i < r; i++) {
                            for (int j = 0; j < c; j++) {
                                t[j][i] = matrix[i][j];
                            }
                        }
                        return t;
                    }
                }
                """;

        list.add(new BenchmarkPair("TEST-P7-A", "TEST", "Prob-7-MatrixMul", "Cat-A", 1, "Identical MatrixMul", p7Base, p7Base));
        list.add(new BenchmarkPair("TEST-P7-B", "TEST", "Prob-7-MatrixMul", "Cat-B", 1, "Renamed MatrixMul", p7Base, p7CatB));
        list.add(new BenchmarkPair("TEST-P7-C", "TEST", "Prob-7-MatrixMul", "Cat-C", 1, "Flattened 1D Array Access", p7Base, p7CatC));
        list.add(new BenchmarkPair("TEST-P7-D", "TEST", "Prob-7-MatrixMul", "Cat-D", 1, "Transposed Dot-Product Multiplication", p7Base, p7CatD));
        list.add(new BenchmarkPair("TEST-P7-E", "TEST", "Prob-7-MatrixMul", "Cat-E", 0, "Matrix Mul vs Transpose Only", p7Base, p7CatE));

        // P8: MathRecursion
        String p8Base = """
                public class MathTools {
                    public static int gcd(int a, int b) {
                        while (b != 0) {
                            int t = b;
                            b = a % b;
                            a = t;
                        }
                        return a;
                    }
                    public static long factorial(int n) {
                        long res = 1;
                        for (int i = 2; i <= n; i++) {
                            res *= i;
                        }
                        return res;
                    }
                }
                """;
        String p8CatB = """
                public class ArithmeticUtil {
                    public static int findGcd(int x, int y) {
                        while (y != 0) {
                            int rem = x % y;
                            x = y;
                            y = rem;
                        }
                        return x;
                    }
                    public static long computeFact(int num) {
                        long total = 1;
                        for (int k = 2; k <= num; k++) {
                            total *= k;
                        }
                        return total;
                    }
                }
                """;
        String p8CatC = """
                public class RecursiveMath {
                    public static int gcd(int a, int b) {
                        return b == 0 ? a : gcd(b, a % b);
                    }
                    public static long factorial(int n) {
                        return n <= 1 ? 1 : n * factorial(n - 1);
                    }
                }
                """;
        String p8CatD = """
                import java.util.stream.LongStream;
                public class FunctionalMath {
                    public static int gcd(int a, int b) {
                        while (b > 0) {
                            int rem = a - (a / b) * b;
                            a = b;
                            b = rem;
                        }
                        return a;
                    }
                    public static long factorial(int n) {
                        return LongStream.rangeClosed(1, n).reduce(1, (x, y) -> x * y);
                    }
                }
                """;
        String p8CatE = """
                public class ModularMath {
                    public static long modExp(long base, long exp, long mod) {
                        long res = 1;
                        base %= mod;
                        while (exp > 0) {
                            if ((exp & 1) == 1) res = (res * base) % mod;
                            base = (base * base) % mod;
                            exp >>= 1;
                        }
                        return res;
                    }
                }
                """;

        list.add(new BenchmarkPair("TEST-P8-A", "TEST", "Prob-8-MathRecursion", "Cat-A", 1, "Identical MathTools", p8Base, p8Base));
        list.add(new BenchmarkPair("TEST-P8-B", "TEST", "Prob-8-MathRecursion", "Cat-B", 1, "Renamed MathTools", p8Base, p8CatB));
        list.add(new BenchmarkPair("TEST-P8-C", "TEST", "Prob-8-MathRecursion", "Cat-C", 1, "Iterative to Recursive Math", p8Base, p8CatC));
        list.add(new BenchmarkPair("TEST-P8-D", "TEST", "Prob-8-MathRecursion", "Cat-D", 1, "Functional Stream Factorial", p8Base, p8CatD));
        list.add(new BenchmarkPair("TEST-P8-E", "TEST", "Prob-8-MathRecursion", "Cat-E", 0, "GCD vs Modular Exponentiation", p8Base, p8CatE));

        return list;
    }
}
