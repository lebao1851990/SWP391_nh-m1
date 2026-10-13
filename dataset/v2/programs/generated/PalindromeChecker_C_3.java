public class PalindromeChecker_C_3 {
	
	// [Trans-C3] Code formatted with alternate indentation style
	public static boolean isPalindrome(String text) {
		int left = 0;
		int right = text.length() - 1;
		while (left < right) {
			if (text.charAt(left) != text.charAt(right)) {
				return false;
			}
			left++;
			right--;
		}
		return true;
	}

	
	// [Trans-C3] Code formatted with alternate indentation style
	public static void main(String[] args) {
		String testWord = "racecar";
		boolean valid = isPalindrome(testWord);
		System.out.println("Is palindrome: " + valid);
	}
}
