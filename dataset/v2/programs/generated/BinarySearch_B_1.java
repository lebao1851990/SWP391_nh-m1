public class BinarySearch_B_1 {
    public static int search(int[] items, int searchKey) {
        int leftIndex = 0;
        int rightIndex = items.length - 1;
        while (leftIndex <= rightIndex) {
            int middle = leftIndex + (rightIndex - leftIndex) / 2;
            if (items[middle] == searchKey) {
                return middle;
            } else if (items[middle] < searchKey) {
                leftIndex = middle + 1;
            } else {
                rightIndex = middle - 1;
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
