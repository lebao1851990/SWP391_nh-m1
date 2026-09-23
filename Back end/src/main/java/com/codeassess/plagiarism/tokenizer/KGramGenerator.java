package com.codeassess.plagiarism.tokenizer;

import com.codeassess.plagiarism.parser.AstNode;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

@Component
public class KGramGenerator {

    private final int k;

    public KGramGenerator(@Value("${plagiarism.kgram-size:5}") int k) {
        this.k = k;
    }

    public List<KGram> generateKGrams(List<AstNode> sequence) {
        List<KGram> kgrams = new ArrayList<>();

        if (sequence == null || sequence.size() < k) {
            // Sequence shorter than K: we can emit one single k-gram encompassing everything
            // Or return empty. The requirement says: "Test sequences shorter than K-gram: 0-4 tokens -> no K-gram"
            // Wait, "0-4 tokens -> no K-gram" is requested in the testing requirements.
            return kgrams;
        }

        for (int i = 0; i <= sequence.size() - k; i++) {
            StringBuilder sb = new StringBuilder();
            int startLine = sequence.get(i).getStartLine();
            int endLine = sequence.get(i + k - 1).getEndLine();
            
            // Representative node type is just the first node's type. 
            // It will fit in 64 chars easily.
            String representativeType = sequence.get(i).getNodeType();
            if (representativeType.length() > 64) {
                representativeType = representativeType.substring(0, 64);
            }

            for (int j = 0; j < k; j++) {
                sb.append(sequence.get(i + j).getNodeType());
                if (j < k - 1) {
                    sb.append("-");
                }
            }

            kgrams.add(new KGram(sb.toString(), representativeType, startLine, endLine));
        }

        return kgrams;
    }
}
