package com.codeassess.plagiarism.winnowing;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class FingerprintHasherTest {

    private FingerprintHasher hasher;

    @BeforeEach
    void setUp() {
        hasher = new FingerprintHasher();
    }

    @Test
    void testSameKGramProducesSameHash() {
        String kgram = "MethodDeclaration-BlockStmt-ExpressionStmt-MethodCallExpr-SimpleName";
        long hash1 = hasher.hash(kgram);
        long hash2 = hasher.hash(kgram);
        assertEquals(hash1, hash2);
    }

    @Test
    void testDifferentKGramsProduceDifferentHashes() {
        String kgram1 = "MethodDeclaration-BlockStmt-ExpressionStmt-MethodCallExpr-SimpleName";
        String kgram2 = "MethodDeclaration-BlockStmt-IfStmt-ExpressionStmt-SimpleName";
        long hash1 = hasher.hash(kgram1);
        long hash2 = hasher.hash(kgram2);
        assertNotEquals(hash1, hash2);
    }

    @Test
    void testNullHandledProperly() {
        assertEquals(0L, hasher.hash(null));
    }
}
