package com.codeassess.plagiarism.tokenizer;

import com.codeassess.plagiarism.parser.AstNode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

class KGramGeneratorTest {

    private KGramGenerator generator;

    @BeforeEach
    void setUp() {
        generator = new KGramGenerator(5);
    }

    private List<AstNode> createSequence(int size) {
        List<AstNode> seq = new ArrayList<>();
        for (int i = 0; i < size; i++) {
            seq.add(new AstNode("Node" + i, i + 1, i + 1));
        }
        return seq;
    }

    @Test
    void testSequenceShorterThanKReturnsEmpty() {
        List<KGram> result0 = generator.generateKGrams(createSequence(0));
        assertTrue(result0.isEmpty());

        List<KGram> result4 = generator.generateKGrams(createSequence(4));
        assertTrue(result4.isEmpty());
    }

    @Test
    void testSequenceExactlyKReturnsOneKGram() {
        List<AstNode> seq = createSequence(5);
        List<KGram> result = generator.generateKGrams(seq);
        
        assertEquals(1, result.size());
        KGram kg = result.get(0);
        assertEquals("Node0-Node1-Node2-Node3-Node4", kg.getConcatenatedTypeString());
        assertEquals("Node0", kg.getRepresentativeNodeType());
        assertEquals(1, kg.getStartLine());
        assertEquals(5, kg.getEndLine());
    }

    @Test
    void testSequenceLargerThanKReturnsOverlappingKGrams() {
        List<AstNode> seq = createSequence(6);
        List<KGram> result = generator.generateKGrams(seq);
        
        assertEquals(2, result.size());
        assertEquals("Node0-Node1-Node2-Node3-Node4", result.get(0).getConcatenatedTypeString());
        assertEquals(1, result.get(0).getStartLine());
        assertEquals(5, result.get(0).getEndLine());

        assertEquals("Node1-Node2-Node3-Node4-Node5", result.get(1).getConcatenatedTypeString());
        assertEquals("Node1", result.get(1).getRepresentativeNodeType());
        assertEquals(2, result.get(1).getStartLine());
        assertEquals(6, result.get(1).getEndLine());
    }
}
