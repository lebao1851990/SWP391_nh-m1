package com.codeassess.plagiarism.parser;

import com.github.javaparser.ast.CompilationUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class JavaAstParserTest {

    private JavaAstParser parser;

    @BeforeEach
    void setUp() {
        parser = new JavaAstParser();
    }

    @Test
    void testValidSourceParses() {
        String code = "public class Main { public static void main(String[] args) { System.out.println(\"Hello\"); } }";
        CompilationUnit cu = parser.parse(code);
        assertNotNull(cu);
        assertTrue(cu.getClassByName("Main").isPresent());
    }

    @Test
    void testInvalidSourceFailsCleanly() {
        String code = "public class Main { public static void main(String[] args) { System.out.println(\"Hello\" } }"; // missing closing parenthesis
        assertThrows(RuntimeException.class, () -> parser.parse(code));
    }

    @Test
    void testEmptySourceFailsCleanly() {
        assertThrows(IllegalArgumentException.class, () -> parser.parse("   "));
        assertThrows(IllegalArgumentException.class, () -> parser.parse(null));
    }

    @Test
    void testAstContainsExpectedStructuralNodesAndLineMetadata() {
        String code = "public class Main {\n" +
                      "    public void test() {\n" +
                      "        int x = 5;\n" +
                      "    }\n" +
                      "}";
        CompilationUnit cu = parser.parse(code);
        
        var classDecl = cu.getClassByName("Main").orElseThrow();
        assertEquals(1, classDecl.getRange().get().begin.line);
        assertEquals(5, classDecl.getRange().get().end.line);

        var methodDecl = classDecl.getMethodsByName("test").get(0);
        assertEquals(2, methodDecl.getRange().get().begin.line);
        assertEquals(4, methodDecl.getRange().get().end.line);
    }
}
