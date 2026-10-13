public class MatrixMultiply_B_1 {
    public static int[][] multiply(int[][] firstMatrix, int[][] secondMatrix) {
        int rCount = firstMatrix.length;
        int cCount = secondMatrix[0].length;
        int common = secondMatrix.length;
        int[][] resultMatrix = new int[rCount][cCount];
        for (int i = 0; i < rCount; i++) {
            for (int j = 0; j < cCount; j++) {
                int accumulatedSum = 0;
                for (int kIdx = 0; kIdx < common; kIdx++) {
                    accumulatedSum += firstMatrix[i][kIdx] * secondMatrix[kIdx][j];
                }
                resultMatrix[i][j] = accumulatedSum;
            }
        }
        return resultMatrix;
    }

    public static void main(String[] args) {
        int[][] m1 = {{1, 2}, {3, 4}};
        int[][] m2 = {{5, 6}, {7, 8}};
        int[][] res = multiply(m1, m2);
        System.out.println("Matrix resultMatrix element: " + res[0][0]);
    }
}
