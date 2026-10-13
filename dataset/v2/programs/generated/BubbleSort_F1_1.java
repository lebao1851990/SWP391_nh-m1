public class BubbleSort_F1_1 {
    public static void sort(int[] arr) {
        int n = arr.length;
        int i = 0;
        while (i < n - 1) {
            for (int j = 0; j < n - i - 1; j++) {
                if (arr[j] > arr[j + 1]) {
                    int temp = arr[j];
                    arr[j] = arr[j + 1];
                    arr[j + 1] = temp;
                }
            }
            i++;
        }
    }

    public static void main(String[] args) {
        int[] data = {64, 34, 25, 12, 22, 11, 90};
        sort(data);
        System.out.println("Sorted first element: " + data[0]);
    }
}
