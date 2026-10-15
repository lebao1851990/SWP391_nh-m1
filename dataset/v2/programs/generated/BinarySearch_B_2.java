public class BinarySearch_B_2 {
    public static int search(int[] sortedData, int queryValue) {
        int startBound = 0;
        int endBound = sortedData.length - 1;
        while (startBound <= endBound) {
            int centerIndex = startBound + (endBound - startBound) / 2;
            if (sortedData[centerIndex] == queryValue) {
                return centerIndex;
            } else if (sortedData[centerIndex] < queryValue) {
                startBound = centerIndex + 1;
            } else {
                endBound = centerIndex - 1;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        int[] data = {2, 5, 8, 12, 16, 23, 38, 56, 72, 91};
        int result = search(data, 23);
        System.out.println("Found at index: " + result);
    }
}
