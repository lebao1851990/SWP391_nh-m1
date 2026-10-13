public class BubbleSort_B_2 {
    public static void sort(int[] numberArray) {
        int totalItems = numberArray.length;
        for (int outerStep = 0; outerStep < totalItems - 1; outerStep++) {
            for (int cursor = 0; cursor < totalItems - outerStep - 1; cursor++) {
                if (numberArray[cursor] > numberArray[cursor + 1]) {
                    int tempValue = numberArray[cursor];
                    numberArray[cursor] = numberArray[cursor + 1];
                    numberArray[cursor + 1] = tempValue;
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
