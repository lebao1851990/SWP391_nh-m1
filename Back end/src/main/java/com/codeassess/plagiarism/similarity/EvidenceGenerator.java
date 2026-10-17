package com.codeassess.plagiarism.similarity;

import com.codeassess.plagiarism.entity.AstFingerprint;
import com.codeassess.plagiarism.entity.PlagiarismEvidence;
import com.codeassess.plagiarism.entity.SimilarityPair;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Component
public class EvidenceGenerator {

    public List<PlagiarismEvidence> generateEvidence(SimilarityPair pair, List<AstFingerprint> fingerprintsA, List<AstFingerprint> fingerprintsB) {
        Map<Long, List<AstFingerprint>> hashToA = fingerprintsA.stream()
                .collect(Collectors.groupingBy(AstFingerprint::getHashValue));
        Map<Long, List<AstFingerprint>> hashToB = fingerprintsB.stream()
                .collect(Collectors.groupingBy(AstFingerprint::getHashValue));

        Set<Long> sharedHashes = new java.util.HashSet<>(hashToA.keySet());
        sharedHashes.retainAll(hashToB.keySet());

        List<PlagiarismEvidence> evidences = new ArrayList<>();

        for (Long hash : sharedHashes) {
            AstFingerprint a = hashToA.get(hash).get(0);
            AstFingerprint b = hashToB.get(hash).get(0);

            String rawType = a.getNodeType();
            String cleanType = rawType;
            if (cleanType.contains("[")) {
                cleanType = cleanType.substring(0, cleanType.indexOf("["));
            }

            String locA = a.getFilePath() != null ? a.getFilePath() + ":" + a.getStartLine() + "-" + a.getEndLine() : "Lines " + a.getStartLine() + "–" + a.getEndLine();
            String locB = b.getFilePath() != null ? b.getFilePath() + ":" + b.getStartLine() + "-" + b.getEndLine() : "Lines " + b.getStartLine() + "–" + b.getEndLine();

            String description = String.format("Matching AST structure detected around %s.", locA);

            PlagiarismEvidence evidence = PlagiarismEvidence.builder()
                    .similarityPair(pair)
                    .evidenceType(cleanType)
                    .description(description)
                    .startLineA(a.getStartLine())
                    .endLineA(a.getEndLine())
                    .startLineB(b.getStartLine())
                    .endLineB(b.getEndLine())
                    .locationA(locA)
                    .locationB(locB)
                    .build();
            evidences.add(evidence);
        }

        return evidences;
    }
}
