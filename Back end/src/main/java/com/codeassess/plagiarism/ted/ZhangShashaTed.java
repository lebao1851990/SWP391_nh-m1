package com.codeassess.plagiarism.ted;

/**
 * Classical Zhang-Shasha (1989) Tree Edit Distance Algorithm.
 * Computes exact tree edit distance between two ordered labeled trees.
 */
public class ZhangShashaTed {

    private final double insertCost;
    private final double deleteCost;
    private final double substituteCost;

    public ZhangShashaTed() {
        this(1.0, 1.0, 1.0);
    }

    public ZhangShashaTed(double insertCost, double deleteCost, double substituteCost) {
        this.insertCost = insertCost;
        this.deleteCost = deleteCost;
        this.substituteCost = substituteCost;
    }

    public TedResult computeDistance(TedTree tree1, TedTree tree2) {
        long startTime = System.nanoTime();

        if (tree1 == null || tree1.size() == 0) {
            int size2 = tree2 != null ? tree2.size() : 0;
            double dist = size2 * insertCost;
            return buildResult(dist, 0, size2, System.nanoTime() - startTime);
        }
        if (tree2 == null || tree2.size() == 0) {
            int size1 = tree1.size();
            double dist = size1 * deleteCost;
            return buildResult(dist, size1, 0, System.nanoTime() - startTime);
        }

        int m = tree1.size();
        int n = tree2.size();

        double[][] td = new double[m + 1][n + 1];

        int[] kr1 = tree1.getKeyroots();
        int[] kr2 = tree2.getKeyroots();

        for (int i0 : kr1) {
            for (int j0 : kr2) {
                computeForestDistance(tree1, tree2, i0, j0, td);
            }
        }

        double finalDistance = td[m][n];
        long elapsed = System.nanoTime() - startTime;
        return buildResult(finalDistance, m, n, elapsed);
    }

    private void computeForestDistance(TedTree tree1, TedTree tree2, int i0, int j0, double[][] td) {
        int lld1 = tree1.getLld(i0);
        int lld2 = tree2.getLld(j0);

        int len1 = i0 - lld1 + 1;
        int len2 = j0 - lld2 + 1;

        int off1 = lld1 - 1;
        int off2 = lld2 - 1;

        double[][] fd = new double[len1 + 1][len2 + 1];
        fd[0][0] = 0.0;

        for (int m = 1; m <= len1; m++) {
            fd[m][0] = fd[m - 1][0] + deleteCost;
        }

        for (int n = 1; n <= len2; n++) {
            fd[0][n] = fd[0][n - 1] + insertCost;
        }

        for (int m = 1; m <= len1; m++) {
            int u = off1 + m;
            TedNode nodeU = tree1.getNode(u);

            for (int n = 1; n <= len2; n++) {
                int v = off2 + n;
                TedNode nodeV = tree2.getNode(v);

                double costDel = fd[m - 1][n] + deleteCost;
                double costIns = fd[m][n - 1] + insertCost;

                if (tree1.getLld(u) == lld1 && tree2.getLld(v) == lld2) {
                    double renCost = nodeU.getLabel().equals(nodeV.getLabel()) ? 0.0 : substituteCost;
                    double costRen = fd[m - 1][n - 1] + renCost;
                    fd[m][n] = Math.min(costDel, Math.min(costIns, costRen));
                    td[u][v] = fd[m][n];
                } else {
                    int p = tree1.getLld(u) - 1 - off1;
                    int q = tree2.getLld(v) - 1 - off2;
                    double costMatch = fd[p][q] + td[u][v];
                    fd[m][n] = Math.min(costDel, Math.min(costIns, costMatch));
                }
            }
        }
    }

    private TedResult buildResult(double distance, int size1, int size2, long elapsedNanos) {
        double simSymmetric;
        double simMax;

        if (size1 == 0 && size2 == 0) {
            simSymmetric = 100.0;
            simMax = 100.0;
        } else {
            double totalSize = size1 + size2;
            simSymmetric = Math.max(0.0, (totalSize - distance) / totalSize * 100.0);
            double maxSize = Math.max(size1, size2);
            simMax = Math.max(0.0, (1.0 - (distance / maxSize)) * 100.0);
        }

        return new TedResult(distance, size1, size2, simSymmetric, simMax, elapsedNanos);
    }

    public static class TedResult {
        private final double distance;
        private final int size1;
        private final int size2;
        private final double similaritySymmetric;
        private final double similarityMax;
        private final long elapsedNanos;

        public TedResult(double distance, int size1, int size2, double similaritySymmetric, double similarityMax, long elapsedNanos) {
            this.distance = distance;
            this.size1 = size1;
            this.size2 = size2;
            this.similaritySymmetric = similaritySymmetric;
            this.similarityMax = similarityMax;
            this.elapsedNanos = elapsedNanos;
        }

        public double getDistance() {
            return distance;
        }

        public int getSize1() {
            return size1;
        }

        public int getSize2() {
            return size2;
        }

        public double getSimilaritySymmetric() {
            return similaritySymmetric;
        }

        public double getSimilarityMax() {
            return similarityMax;
        }

        public long getElapsedNanos() {
            return elapsedNanos;
        }

        public double getElapsedMillis() {
            return elapsedNanos / 1_000_000.0;
        }

        @Override
        public String toString() {
            return String.format("TedResult(distance=%.1f, size1=%d, size2=%d, simSym=%.2f%%, simMax=%.2f%%, time=%.2fms)",
                    distance, size1, size2, similaritySymmetric, similarityMax, getElapsedMillis());
        }
    }
}

