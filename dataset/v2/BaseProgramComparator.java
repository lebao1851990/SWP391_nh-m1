package com.codeassess.v2;

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

import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;
import java.util.stream.Collectors;

public class BaseProgramComparator {

    public static void main(String[] args) throws Exception {
        Locale.setDefault(Locale.US);
        Path root = Paths.get(".").toAbsolutePath().normalize();
        if (root.getFileName().toString().equalsIgnoreCase("Back end")) {
            root = root.getParent();
        }
        Path baseDir = root.resolve("dataset").resolve("v2").resolve("programs").resolve("base");

        JavaAstParser parser = new JavaAstParser();
        AstNormalizer normalizer = new AstNormalizer();
        KGramGenerator kGramGen = new KGramGenerator(5);
        FingerprintHasher hasher = new FingerprintHasher();
        Winnower winnower = new Winnower(4);
        JaccardSimilarityCalculator calc = new JaccardSimilarityCalculator();

        List<String> files = List.of(
                "BinarySearch.java",
                "BubbleSort.java",
                "MatrixMultiply.java",
                "SinglyLinkedList.java",
                "PalindromeChecker.java"
        );

        Map<String, Set<Long>> fingerprints = new LinkedHashMap<>();
        Map<String, Integer> nodeCounts = new LinkedHashMap<>();
        Map<String, Integer> kgramCounts = new LinkedHashMap<>();

        System.out.println("=== DATASET V2 BASE PROGRAMS AST ANALYSIS ===");
        for (String file : files) {
            Path p = baseDir.resolve(file);
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

            fingerprints.put(file, set);
            nodeCounts.put(file, nodes.size());
            kgramCounts.put(file, kgrams.size());

            System.out.printf("%-22s : %2d AST nodes, %2d 5-grams, %2d winnowed fingerprints%n",
                    file, nodes.size(), kgrams.size(), set.size());
        }

        System.out.println("\n=== PAIRWISE JACCARD SIMILARITY (10 UNRELATED BASE PAIRS) ===");
        boolean anyAboveThreshold = false;
        for (int i = 0; i < files.size(); i++) {
            for (int j = i + 1; j < files.size(); j++) {
                String fA = files.get(i);
                String fB = files.get(j);
                Set<Long> fpA = fingerprints.get(fA);
                Set<Long> fpB = fingerprints.get(fB);

                SimilarityResult res = calc.calculate(fpA, fpB);
                double sim = res.getSimilarityRate().doubleValue();
                boolean flagged = res.isFlagged();
                if (flagged) anyAboveThreshold = true;

                System.out.printf("%-20s vs %-20s | Hashes: %2d vs %2d | Shared: %2d | Sim: %5.2f%% | Flagged (>= 70%%): %b%n",
                        fA.replace(".java", ""), fB.replace(".java", ""),
                        fpA.size(), fpB.size(), res.getSharedHashesCount(), sim, flagged);
            }
        }

        System.out.println("--------------------------------------------------");
        System.out.printf("Did any unrelated base pair exceed 70.00%%? %b%n", anyAboveThreshold);
        if (!anyAboveThreshold) {
            System.out.println("[SUCCESS] Clean negative separation confirmed! All unrelated base pairs score far below threshold.");
        }
    }
}
