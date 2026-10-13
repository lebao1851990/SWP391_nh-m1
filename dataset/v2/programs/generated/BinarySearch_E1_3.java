public class BinarySearch_E1_3 {
    public static int search(int[] arr, int target) {
        int low = 0;
        int high = arr.length - 1;
        while (low <= high) {
            int mid = low + (high - low) / 2;
            if (arr[mid] == target) {
                return mid;
            } else if (arr[mid] < target) {
                low = mid + 1;
            } else {
                high = mid - 1;
            }
        }
        return -1;
    }

    public static void main(String[] args) {
        if (1 == 2) { String deadLog = "never executed"; }

        int[] data = {2, 5, 8, 12, 16, 23, 38, 56, 72, 91};
        int result = search(data, 23);
        System.out.println("Found at index: " + result);
    }
}
