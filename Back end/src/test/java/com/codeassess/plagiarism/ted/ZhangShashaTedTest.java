package com.codeassess.plagiarism.ted;

import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

public class ZhangShashaTedTest {

    private final ZhangShashaTed ted = new ZhangShashaTed();

    @Test
    @DisplayName("1. Identical single node trees have distance 0 and 100% similarity")
    void testIdenticalSingleNode() {
        TedNode r1 = new TedNode("A");
        TedNode r2 = new TedNode("A");

        TedTree t1 = new TedTree(r1);
        TedTree t2 = new TedTree(r2);

        ZhangShashaTed.TedResult res = ted.computeDistance(t1, t2);
        assertEquals(0.0, res.getDistance(), 1e-6);
        assertEquals(100.0, res.getSimilaritySymmetric(), 1e-6);
        assertEquals(100.0, res.getSimilarityMax(), 1e-6);
    }

    @Test
    @DisplayName("2. Single node substitution has distance 1.0")
    void testSingleNodeSubstitution() {
        TedNode r1 = new TedNode("A");
        TedNode r2 = new TedNode("B");

        TedTree t1 = new TedTree(r1);
        TedTree t2 = new TedTree(r2);

        ZhangShashaTed.TedResult res = ted.computeDistance(t1, t2);
        assertEquals(1.0, res.getDistance(), 1e-6);
        assertEquals(50.0, res.getSimilaritySymmetric(), 1e-6);
        assertEquals(0.0, res.getSimilarityMax(), 1e-6);
    }

    @Test
    @DisplayName("3. Symmetry property: TED(T1, T2) == TED(T2, T1)")
    void testSymmetry() {
        // T1: root(A) -> child(B), child(C)
        TedNode r1 = new TedNode("A");
        r1.addChild(new TedNode("B"));
        r1.addChild(new TedNode("C"));
        TedTree t1 = new TedTree(r1);

        // T2: root(A) -> child(B), child(D) -> grandChild(E)
        TedNode r2 = new TedNode("A");
        r2.addChild(new TedNode("B"));
        TedNode d = new TedNode("D");
        d.addChild(new TedNode("E"));
        r2.addChild(d);
        TedTree t2 = new TedTree(r2);

        ZhangShashaTed.TedResult res12 = ted.computeDistance(t1, t2);
        ZhangShashaTed.TedResult res21 = ted.computeDistance(t2, t1);

        assertEquals(res12.getDistance(), res21.getDistance(), 1e-6);
        assertEquals(res12.getSimilaritySymmetric(), res21.getSimilaritySymmetric(), 1e-6);
    }

    @Test
    @DisplayName("4. Triangle Inequality: TED(A, C) <= TED(A, B) + TED(B, C)")
    void testTriangleInequality() {
        TedNode a = new TedNode("Root");
        a.addChild(new TedNode("X"));
        TedTree ta = new TedTree(a);

        TedNode b = new TedNode("Root");
        b.addChild(new TedNode("Y"));
        TedTree tb = new TedTree(b);

        TedNode c = new TedNode("Root");
        TedNode z = new TedNode("Z");
        z.addChild(new TedNode("W"));
        c.addChild(z);
        TedTree tc = new TedTree(c);

        double distAB = ted.computeDistance(ta, tb).getDistance();
        double distBC = ted.computeDistance(tb, tc).getDistance();
        double distAC = ted.computeDistance(ta, tc).getDistance();

        assertTrue(distAC <= distAB + distBC + 1e-6,
                "Triangle inequality must hold: distAC=" + distAC + " <= " + (distAB + distBC));
    }

    @Test
    @DisplayName("5. Canonical Zhang-Shasha textbook example")
    void testTextbookExample() {
        // Tree 1: f -> (a, d -> (c, b))
        TedNode f1 = new TedNode("f");
        f1.addChild(new TedNode("a"));
        TedNode d1 = new TedNode("d");
        d1.addChild(new TedNode("c"));
        d1.addChild(new TedNode("b"));
        f1.addChild(d1);
        TedTree t1 = new TedTree(f1);

        // Tree 2: f -> (c, d -> (a, b))
        TedNode f2 = new TedNode("f");
        f2.addChild(new TedNode("c"));
        TedNode d2 = new TedNode("d");
        d2.addChild(new TedNode("a"));
        d2.addChild(new TedNode("b"));
        f2.addChild(d2);
        TedTree t2 = new TedTree(f2);

        ZhangShashaTed.TedResult res = ted.computeDistance(t1, t2);
        // Swapping/renaming a and c across siblings:
        // Delete a, insert c, or 2 renames
        assertTrue(res.getDistance() > 0.0);
        assertEquals(res.getDistance(), ted.computeDistance(t2, t1).getDistance());
    }

    @Test
    @DisplayName("6. Java AST from source code identical test")
    void testJavaAstIdentical() {
        String code = """
                public class Demo {
                    public int sum(int a, int b) {
                        return a + b;
                    }
                }
                """;
        CompilationUnit cu1 = StaticJavaParser.parse(code);
        CompilationUnit cu2 = StaticJavaParser.parse(code);

        TedTree t1 = TedTree.fromCompilationUnit(cu1);
        TedTree t2 = TedTree.fromCompilationUnit(cu2);

        assertTrue(t1.size() > 5);
        ZhangShashaTed.TedResult res = ted.computeDistance(t1, t2);
        assertEquals(0.0, res.getDistance(), 1e-6);
        assertEquals(100.0, res.getSimilaritySymmetric(), 1e-6);
    }

    @Test
    @DisplayName("7. Java AST with identifier renaming should have distance 0.0 under normalization")
    void testJavaAstIdentifierRenaming() {
        String code1 = """
                public class Demo {
                    public int sum(int a, int b) {
                        int res = a + b;
                        return res;
                    }
                }
                """;
        String code2 = """
                public class Calculation {
                    public int compute(int x, int y) {
                        int output = x + y;
                        return output;
                    }
                }
                """;
        CompilationUnit cu1 = StaticJavaParser.parse(code1);
        CompilationUnit cu2 = StaticJavaParser.parse(code2);

        TedTree t1 = TedTree.fromCompilationUnit(cu1);
        TedTree t2 = TedTree.fromCompilationUnit(cu2);

        assertEquals(t1.size(), t2.size());
        ZhangShashaTed.TedResult res = ted.computeDistance(t1, t2);
        assertEquals(0.0, res.getDistance(), 1e-6, "Stripped identifiers should result in 0 tree edit distance");
        assertEquals(100.0, res.getSimilaritySymmetric(), 1e-6);
    }
}

