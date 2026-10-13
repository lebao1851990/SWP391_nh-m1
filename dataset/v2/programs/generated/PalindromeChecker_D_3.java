public class PalindromeChecker_D_3 {
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

    public static void main(String[] args) {
        String testWord = "deified";
        boolean valid = isPalindrome(testWord);
        System.out.println("Is palindrome: " + valid);
    }
}
