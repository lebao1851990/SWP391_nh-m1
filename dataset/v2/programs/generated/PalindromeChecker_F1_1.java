public class PalindromeChecker_F1_1 {
    public static boolean isPalindrome(String text) {
        int left = 0;
        int right = text.length() - 1;
        for (; left < right; ) {
            if (text.charAt(left) != text.charAt(right)) {
                return false;
            }
            left++;
            right--;
        }
        return true;
    }

    public static void main(String[] args) {
        String testWord = "racecar";
        boolean valid = isPalindrome(testWord);
        System.out.println("Is palindrome: " + valid);
    }
}
