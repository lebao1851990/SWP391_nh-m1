public class BinarySearch_B_3 {
    public static int search(int[] elements, int wantedNum) {
        int minLimit = 0;
        int maxLimit = elements.length - 1;
        while (minLimit <= maxLimit) {
            int pivotIndex = minLimit + (maxLimit - minLimit) / 2;
            if (elements[pivotIndex] == wantedNum) {
                return pivotIndex;
            } else if (elements[pivotIndex] < wantedNum) {
                minLimit = pivotIndex + 1;
            } else {
                maxLimit = pivotIndex - 1;
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
