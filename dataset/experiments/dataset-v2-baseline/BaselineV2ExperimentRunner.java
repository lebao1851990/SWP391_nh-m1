package com.codeassess.experiment.v2;

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
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class BaselineV2ExperimentRunner {

    private final Path rootDir;
    private final Path v2Dir;
    private final Path resultsDir;

    private final JavaAstParser parser = new JavaAstParser();
    private final AstNormalizer normalizer = new AstNormalizer();
    private final KGramGenerator kGramGen = new KGramGenerator(5); // K = 5
    private final FingerprintHasher hasher = new FingerprintHasher(); // FNV-1a 64-bit
    private final Winnower winnower = new Winnower(4); // W = 4
    private final JaccardSimilarityCalculator calc = new JaccardSimilarityCalculator(); // 70.00% threshold

    private final Map<String, Set<Long>> fpCache = new HashMap<>();

    public BaselineV2ExperimentRunner(Path rootDir) {
        this.rootDir = rootDir;
        this.v2Dir = rootDir.resolve("dataset").resolve("v2");
        this.resultsDir = rootDir.resolve("dataset").resolve("experiments").resolve("dataset-v2-baseline").resolve("results");
    }

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        Path root = Paths.get(".").toAbsolutePath().normalize();
        if (root.getFileName().toString().equalsIgnoreCase("Back end")) {
            root = root.getParent();
        }
        System.out.println("Running BaselineV2ExperimentRunner from: " + root);
        BaselineV2ExperimentRunner runner = new BaselineV2ExperimentRunner(root);
        runner.execute();
    }

    public void execute() throws Exception {
        Files.createDirectories(resultsDir);

        Path pairsCsv = v2Dir.resolve("pairs.csv");
        List<String> lines = Files.readAllLines(pairsCsv, StandardCharsets.UTF_8);
        System.out.println("1. Loaded pairs.csv lines: " + (lines.size() - 1));

        // Evaluate all pairs
        List<PairResult> results = new ArrayList<>();
        long startTime = System.nanoTime();

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

            Set<Long> fpA = getOrCompute(progA);
            Set<Long> fpB = getOrCompute(progB);

            SimilarityResult sim = calc.calculate(fpA, fpB);
            boolean isPlag = "PLAGIARISM".equalsIgnoreCase(groundTruth);
            boolean predFlag = sim.isFlagged();

            String outcome;
            if (isPlag && predFlag) outcome = "TP";
            else if (!isPlag && !predFlag) outcome = "TN";
            else if (!isPlag && predFlag) outcome = "FP";
            else outcome = "FN";

            results.add(new PairResult(pairId, progA, progB, cat, trans, prov, semRel, groundTruth,
                    fpA.size(), fpB.size(), sim.getSharedHashesCount(),
                    sim.getSimilarityRate().doubleValue(), predFlag, outcome));
        }
        long durationMs = (System.nanoTime() - startTime) / 1_000_000;
        System.out.printf("2. Evaluated all %d pairs in %d ms%n", results.size(), durationMs);

        // Export pair results
        exportPairResults(results);

        // Compute and export Confusion Matrix
        ConfusionMatrix cm = computeConfusionMatrix(results);
        exportConfusionMatrix(cm);

        // Compute and export Category Metrics
        Map<String, CategoryMetric> catMetrics = computeCategoryMetrics(results);
        exportCategoryMetrics(catMetrics);

        // Compute and export Threshold Analysis
        List<ThresholdMetric> threshMetrics = computeThresholdAnalysis(results);
        exportThresholdAnalysis(threshMetrics);

        System.out.println("\n=== DATASET V2 BASELINE EVALUATION SUMMARY ===");
        System.out.printf("Total Pairs: %d%n", cm.total());
        System.out.printf("TP: %d | TN: %d | FP: %d | FN: %d%n", cm.tp, cm.tn, cm.fp, cm.fn);
        System.out.printf("Accuracy:    %.2f%% (%.4f)%n", cm.accuracy() * 100, cm.accuracy());
        System.out.printf("Precision:   %.2f%% (%.4f)%n", cm.precision() * 100, cm.precision());
        System.out.printf("Recall:      %.2f%% (%.4f)%n", cm.recall() * 100, cm.recall());
        System.out.printf("Specificity: %.2f%% (%.4f)%n", cm.specificity() * 100, cm.specificity());
        System.out.printf("F1-Score:    %.4f%n", cm.f1());
        System.out.printf("FPR:         %.2f%% (%.4f)%n", cm.fpr() * 100, cm.fpr());
        System.out.printf("FNR:         %.2f%% (%.4f)%n", cm.fnr() * 100, cm.fnr());
    }

    private Set<Long> getOrCompute(String name) throws IOException {
        if (fpCache.containsKey(name)) return fpCache.get(name);
        Path p = name.contains("_") ?
                v2Dir.resolve("programs").resolve("generated").resolve(name) :
                v2Dir.resolve("programs").resolve("base").resolve(name);

        String code = Files.readString(p, StandardCharsets.UTF_8);
        CompilationUnit cu = parser.parse(code);
        List<AstNode> nodes = normalizer.normalize(cu);
        List<KGram> kgrams = kGramGen.generateKGrams(nodes);

        List<HashedKGram> hashed = new ArrayList<>();
        for (int i = 0; i < kgrams.size(); i++) {
            KGram kg = kgrams.get(i);
            long h = hasher.hash(kg.getConcatenatedTypeString());
            hashed.add(new HashedKGram(h, i, kg.getRepresentativeNodeType(), kg.getStartLine(), kg.getEndLine()));
        }
        List<HashedKGram> winnowed = winnower.winnow(hashed);
        Set<Long> set = winnowed.stream().map(HashedKGram::getHash).collect(Collectors.toSet());

        fpCache.put(name, set);
        return set;
    }

    private void exportPairResults(List<PairResult> results) throws IOException {
        Path p = resultsDir.resolve("baseline_v2_pair_results.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8))) {
            pw.println("pair_id,program_a,program_b,category,transformation,provenance,semantic_relationship,ground_truth,hashes_a,hashes_b,shared_hashes,similarity_rate,predicted_flag,outcome");
            for (PairResult r : results) {
                pw.printf("%s,%s,%s,%s,%s,%s,%s,%s,%d,%d,%d,%.2f,%b,%s%n",
                        r.pairId, r.progA, r.progB, r.cat, r.trans, r.prov, r.semRel, r.groundTruth,
                        r.hashesA, r.hashesB, r.sharedHashes, r.similarityRate, r.predictedFlag, r.outcome);
            }
        }
        System.out.println("   Saved: " + p);
    }

    private ConfusionMatrix computeConfusionMatrix(List<PairResult> results) {
        int tp = 0, tn = 0, fp = 0, fn = 0;
        for (PairResult r : results) {
            switch (r.outcome) {
                case "TP" -> tp++;
                case "TN" -> tn++;
                case "FP" -> fp++;
                case "FN" -> fn++;
            }
        }
        return new ConfusionMatrix(tp, tn, fp, fn);
    }

    private void exportConfusionMatrix(ConfusionMatrix cm) throws IOException {
        Path p = resultsDir.resolve("confusion_matrix_v2.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8))) {
            pw.println("metric,value");
            pw.printf("TP,%d%n", cm.tp);
            pw.printf("TN,%d%n", cm.tn);
            pw.printf("FP,%d%n", cm.fp);
            pw.printf("FN,%d%n", cm.fn);
            pw.printf("total,%d%n", cm.total());
            pw.printf("accuracy,%.4f%n", cm.accuracy());
            pw.printf("precision,%.4f%n", cm.precision());
            pw.printf("recall,%.4f%n", cm.recall());
            pw.printf("f1,%.4f%n", cm.f1());
            pw.printf("specificity,%.4f%n", cm.specificity());
            pw.printf("fpr,%.4f%n", cm.fpr());
            pw.printf("fnr,%.4f%n", cm.fnr());
        }
        System.out.println("   Saved: " + p);
    }

    private Map<String, CategoryMetric> computeCategoryMetrics(List<PairResult> results) {
        Map<String, List<PairResult>> grouped = results.stream().collect(Collectors.groupingBy(r -> r.cat));
        Map<String, CategoryMetric> map = new TreeMap<>();

        for (Map.Entry<String, List<PairResult>> e : grouped.entrySet()) {
            String cat = e.getKey();
            List<PairResult> list = e.getValue();
            int tp = 0, tn = 0, fp = 0, fn = 0;
            List<Double> sims = new ArrayList<>();
            for (PairResult r : list) {
                sims.add(r.similarityRate);
                switch (r.outcome) {
                    case "TP" -> tp++;
                    case "TN" -> tn++;
                    case "FP" -> fp++;
                    case "FN" -> fn++;
                }
            }
            Collections.sort(sims);
            double min = sims.get(0);
            double max = sims.get(sims.size() - 1);
            double mean = sims.stream().mapToDouble(Double::doubleValue).average().orElse(0.0);
            double median = sims.size() % 2 == 0 ?
                    (sims.get(sims.size() / 2 - 1) + sims.get(sims.size() / 2)) / 2.0 :
                    sims.get(sims.size() / 2);

            map.put(cat, new CategoryMetric(cat, list.size(), tp, tn, fp, fn, min, max, mean, median));
        }
        return map;
    }

    private void exportCategoryMetrics(Map<String, CategoryMetric> metrics) throws IOException {
        Path p = resultsDir.resolve("category_metrics_v2.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8))) {
            pw.println("category,pair_count,tp,tn,fp,fn,precision,recall,f1,min_similarity,max_similarity,mean_similarity,median_similarity");
            for (CategoryMetric cm : metrics.values()) {
                pw.printf("%s,%d,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.2f,%.2f,%.2f,%.2f%n",
                        cm.category, cm.pairCount, cm.tp, cm.tn, cm.fp, cm.fn,
                        cm.precision(), cm.recall(), cm.f1(),
                        cm.minSimilarity, cm.maxSimilarity, cm.meanSimilarity, cm.medianSimilarity);
            }
        }
        System.out.println("   Saved: " + p);
    }

    private List<ThresholdMetric> computeThresholdAnalysis(List<PairResult> results) {
        List<ThresholdMetric> list = new ArrayList<>();
        double[] thresholds = {10.0, 20.0, 30.0, 40.0, 50.0, 60.0, 65.0, 70.0, 75.0, 80.0, 85.0, 90.0, 95.0, 100.0};
        for (double t : thresholds) {
            int tp = 0, tn = 0, fp = 0, fn = 0;
            for (PairResult r : results) {
                boolean isPlag = "PLAGIARISM".equalsIgnoreCase(r.groundTruth);
                boolean pred = r.similarityRate >= t;
                if (isPlag && pred) tp++;
                else if (!isPlag && !pred) tn++;
                else if (!isPlag && pred) fp++;
                else fn++;
            }
            list.add(new ThresholdMetric(t, new ConfusionMatrix(tp, tn, fp, fn)));
        }
        return list;
    }

    private void exportThresholdAnalysis(List<ThresholdMetric> metrics) throws IOException {
        Path p = resultsDir.resolve("threshold_analysis_v2.csv");
        try (PrintWriter pw = new PrintWriter(Files.newBufferedWriter(p, StandardCharsets.UTF_8))) {
            pw.println("threshold,tp,tn,fp,fn,accuracy,precision,recall,f1,specificity,fpr,fnr");
            for (ThresholdMetric tm : metrics) {
                ConfusionMatrix cm = tm.cm;
                pw.printf("%.2f,%d,%d,%d,%d,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f,%.4f%n",
                        tm.threshold, cm.tp, cm.tn, cm.fp, cm.fn,
                        cm.accuracy(), cm.precision(), cm.recall(), cm.f1(),
                        cm.specificity(), cm.fpr(), cm.fnr());
            }
        }
        System.out.println("   Saved: " + p);
    }

    private record PairResult(String pairId, String progA, String progB, String cat, String trans,
                              String prov, String semRel, String groundTruth, int hashesA, int hashesB,
                              int sharedHashes, double similarityRate, boolean predictedFlag, String outcome) {}

    public record ConfusionMatrix(int tp, int tn, int fp, int fn) {
        public int total() { return tp + tn + fp + fn; }
        public double accuracy() { return total() == 0 ? 0 : (double) (tp + tn) / total(); }
        public double precision() { return (tp + fp) == 0 ? 0 : (double) tp / (tp + fp); }
        public double recall() { return (tp + fn) == 0 ? 0 : (double) tp / (tp + fn); }
        public double specificity() { return (tn + fp) == 0 ? 0 : (double) tn / (tn + fp); }
        public double f1() {
            double p = precision(), r = recall();
            return (p + r) == 0 ? 0 : (2.0 * p * r) / (p + r);
        }
        public double fpr() { return (fp + tn) == 0 ? 0 : (double) fp / (fp + tn); }
        public double fnr() { return (fn + tp) == 0 ? 0 : (double) fn / (fn + tp); }
    }

    public record CategoryMetric(String category, int pairCount, int tp, int tn, int fp, int fn,
                                 double minSimilarity, double maxSimilarity, double meanSimilarity, double medianSimilarity) {
        public double precision() { return (tp + fp) == 0 ? 0 : (double) tp / (tp + fp); }
        public double recall() { return (tp + fn) == 0 ? 0 : (double) tp / (tp + fn); }
        public double f1() {
            double p = precision(), r = recall();
            return (p + r) == 0 ? 0 : (2.0 * p * r) / (p + r);
        }
    }

    public record ThresholdMetric(double threshold, ConfusionMatrix cm) {}
}
