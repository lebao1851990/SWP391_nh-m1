public class SinglyLinkedList_F1_3 {
    static class Node {
        int val;
        Node next;
        Node(int val) {
            this.val = val;
            this.next = null;
        }
    }

    public static int countNodes(Node head) {
        int count = 0;
        Node curr = head;
        for (; curr != null; ) {
            count++;
            curr = curr.next;
        }
        return count;
    }

    public static void main(String[] args) {
        Node first = new Node(10);
        first.next = new Node(20);
        first.next.next = new Node(30);
        int total = countNodes(first);
        System.out.println("Total node count: " + total);
    }
}
