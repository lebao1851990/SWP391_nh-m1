public class PalindromeChecker_B_1 {
    public static boolean isPalindrome(String candidateStr) {
        int leftCursor = 0;
        int rightCursor = candidateStr.length() - 1;
        while (leftCursor < rightCursor) {
            if (candidateStr.charAt(leftCursor) != candidateStr.charAt(rightCursor)) {
                return false;
            }
            leftCursor++;
            rightCursor--;
        }
        return true;
    }

    public static void main(String[] args) {
        String testWord = "racecar";
        boolean valid = isPalindrome(testWord);
        System.out.println("Is palindrome: " + valid);
    }
}
