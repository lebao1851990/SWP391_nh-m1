public class BubbleSort_B_3 {
    public static void sort(int[] elementList) {
        int itemCount = elementList.length;
        for (int stage = 0; stage < itemCount - 1; stage++) {
            for (int pointer = 0; pointer < itemCount - stage - 1; pointer++) {
                if (elementList[pointer] > elementList[pointer + 1]) {
                    int cacheVar = elementList[pointer];
                    elementList[pointer] = elementList[pointer + 1];
                    elementList[pointer + 1] = cacheVar;
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
