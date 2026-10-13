public class MatrixMultiply_B_2 {
    public static int[][] multiply(int[][] matA, int[][] matB) {
        int numRows = matA.length;
        int numCols = matB[0].length;
        int common = matB.length;
        int[][] matrixProduct = new int[numRows][numCols];
        for (int i = 0; i < numRows; i++) {
            for (int j = 0; j < numCols; j++) {
                int cellTotal = 0;
                for (int stepK = 0; stepK < common; stepK++) {
                    cellTotal += matA[i][stepK] * matB[stepK][j];
                }
                matrixProduct[i][j] = cellTotal;
            }
        }
        return matrixProduct;
    }

    public static void main(String[] args) {
        int[][] m1 = {{1, 2}, {3, 4}};
        int[][] m2 = {{5, 6}, {7, 8}};
        int[][] res = multiply(m1, m2);
        System.out.println("Matrix matrixProduct element: " + res[0][0]);
    }
}
