public class MatrixMultiply_F1_1 {
    public static int[][] multiply(int[][] a, int[][] b) {
        int rows = a.length;
        int cols = b[0].length;
        int common = b.length;
        int[][] product = new int[rows][cols];
        int i = 0;
        while (i < rows) {
            for (int j = 0; j < cols; j++) {
                int sum = 0;
                for (int k = 0; k < common; k++) {
                    sum += a[i][k] * b[k][j];
                }
                product[i][j] = sum;
            }
            i++;
        }
        return product;
    }

    public static void main(String[] args) {
        int[][] m1 = {{1, 2}, {3, 4}};
        int[][] m2 = {{5, 6}, {7, 8}};
        int[][] res = multiply(m1, m2);
        System.out.println("Matrix product element: " + res[0][0]);
    }
}
