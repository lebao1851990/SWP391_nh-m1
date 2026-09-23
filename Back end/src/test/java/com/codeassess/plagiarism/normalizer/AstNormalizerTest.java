package com.codeassess.plagiarism.normalizer;

import com.codeassess.plagiarism.parser.AstNode;
import com.codeassess.plagiarism.parser.JavaAstParser;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.*;

class AstNormalizerTest {

    private JavaAstParser parser;
    private AstNormalizer normalizer;

    @BeforeEach
    void setUp() {
        parser = new JavaAstParser();
        normalizer = new AstNormalizer();
    }

    private List<String> getNormalizedTypes(String code) {
        return normalizer.normalize(parser.parse(code)).stream()
                .map(AstNode::getNodeType)
                .collect(Collectors.toList());
    }

    @Test
    void testVariableRenamingProducesEquivalentStructure() {
        String code1 = "class A { int calculateTotal(int price) { int result = price * 2; return result; } }";
        String code2 = "class A { int calculateTotal(int value) { int output = value * 2; return output; } }";

        List<String> types1 = getNormalizedTypes(code1);
        List<String> types2 = getNormalizedTypes(code2);

        assertEquals(types1, types2);
    }

    @Test
    void testCommentsAndFormattingDoNotAlterStructure() {
        String code1 = "class A { int calc(int a) { return a * 2; } }";
        String code2 = "class A {\n" +
                       "    // This calculates something\n" +
                       "    int calc(int a) {\n" +
                       "        /* block comment */\n" +
                       "        return a * 2; \n" +
                       "    }\n" +
                       "}";

        List<String> types1 = getNormalizedTypes(code1);
        List<String> types2 = getNormalizedTypes(code2);

        assertEquals(types1, types2);
    }

    @Test
    void testLiteralsRemainDistinguishable() {
        String code1 = "class A { int calc(int price) { return price * 2; } }";
        String code2 = "class A { int calc(int price) { return price * 100; } }";

        List<String> types1 = getNormalizedTypes(code1);
        List<String> types2 = getNormalizedTypes(code2);

        assertNotEquals(types1, types2);
        assertTrue(types1.contains("IntegerLiteralExpr[2]"));
        assertTrue(types2.contains("IntegerLiteralExpr[100]"));
    }

    @Test
    void testStructurallyDifferentCodeRemainsDistinguishable() {
        String code1 = "class A { int calc(int a) { return a * 2; } }";
        String code2 = "class A { int calc(int a) { if (a > 0) return a * 2; return 0; } }";

        List<String> types1 = getNormalizedTypes(code1);
        List<String> types2 = getNormalizedTypes(code2);

        assertNotEquals(types1, types2);
    }
}
