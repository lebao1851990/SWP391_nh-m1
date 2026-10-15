public class BinarySearch_C_3 {
	
	// [Trans-C3] Code formatted with alternate indentation style
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

	
	// [Trans-C3] Code formatted with alternate indentation style
	public static void main(String[] args) {
		int[] data = {2, 5, 8, 12, 16, 23, 38, 56, 72, 91};
		int result = search(data, 23);
		System.out.println("Found at index: " + result);
	}
}
