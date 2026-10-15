public class PalindromeChecker_B_3 {
    public static boolean isPalindrome(String testString) {
        int frontIndex = 0;
        int rearIndex = testString.length() - 1;
        while (frontIndex < rearIndex) {
            if (testString.charAt(frontIndex) != testString.charAt(rearIndex)) {
                return false;
            }
            frontIndex++;
            rearIndex--;
        }
        return true;
    }

    public static void main(String[] args) {
        String testWord = "racecar";
        boolean valid = isPalindrome(testWord);
        System.out.println("Is palindrome: " + valid);
    }
}
