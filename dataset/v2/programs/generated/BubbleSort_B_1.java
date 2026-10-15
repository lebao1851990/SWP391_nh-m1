public class BubbleSort_B_1 {
    public static void sort(int[] values) {
        int length = values.length;
        for (int passIndex = 0; passIndex < length - 1; passIndex++) {
            for (int innerIndex = 0; innerIndex < length - passIndex - 1; innerIndex++) {
                if (values[innerIndex] > values[innerIndex + 1]) {
                    int swapBuffer = values[innerIndex];
                    values[innerIndex] = values[innerIndex + 1];
                    values[innerIndex + 1] = swapBuffer;
                }
            }
        }
    }

    public static void main(String[] args) {
        int[] data = {64, 34, 25, 12, 22, 11, 90};
        sort(data);
        System.out.println("Sorted first element: " + data[0]);
    }
}
