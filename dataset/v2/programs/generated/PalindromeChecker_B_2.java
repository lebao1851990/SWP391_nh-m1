public class PalindromeChecker_B_2 {
    public static boolean isPalindrome(String phrase) {
        int startPtr = 0;
        int endPtr = phrase.length() - 1;
        while (startPtr < endPtr) {
            if (phrase.charAt(startPtr) != phrase.charAt(endPtr)) {
                return false;
            }
            startPtr++;
            endPtr--;
        }
        return true;
    }

    public static void main(String[] args) {
        String testWord = "racecar";
        boolean valid = isPalindrome(testWord);
        System.out.println("Is palindrome: " + valid);
    }
}
