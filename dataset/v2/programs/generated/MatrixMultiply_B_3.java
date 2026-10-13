public class MatrixMultiply_B_3 {
    public static int[][] multiply(int[][] leftGrid, int[][] rightGrid) {
        int rowCount = leftGrid.length;
        int colCount = rightGrid[0].length;
        int common = rightGrid.length;
        int[][] outputGrid = new int[rowCount][colCount];
        for (int i = 0; i < rowCount; i++) {
            for (int j = 0; j < colCount; j++) {
                int runningTotal = 0;
                for (int innerK = 0; innerK < common; innerK++) {
                    runningTotal += leftGrid[i][innerK] * rightGrid[innerK][j];
                }
                outputGrid[i][j] = runningTotal;
            }
        }
        return outputGrid;
    }

    public static void main(String[] args) {
        int[][] m1 = {{1, 2}, {3, 4}};
        int[][] m2 = {{5, 6}, {7, 8}};
        int[][] res = multiply(m1, m2);
        System.out.println("Matrix outputGrid element: " + res[0][0]);
    }
}
