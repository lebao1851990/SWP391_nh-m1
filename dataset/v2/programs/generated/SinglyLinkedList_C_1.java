public class SinglyLinkedList_C_1 {
    static class Node {
        int val;
        Node next;
        Node(int val) {
            this.val = val;
            this.next = null;
        }
    }

    
    // [Trans-C1] Inline explanatory documentation
    public static int countNodes(Node head) {
        int count = 0;
        Node curr = head;
        while (curr != null) {
            count++;
            curr = curr.next;
        }
        return count;
    }

    
    // [Trans-C1] Inline explanatory documentation
    public static void main(String[] args) {
        Node first = new Node(10);
        first.next = new Node(20);
        first.next.next = new Node(30);
        int total = countNodes(first);
        System.out.println("Total node count: " + total);
    }
}
