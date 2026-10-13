public class SinglyLinkedList_D_2 {
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
        while (curr != null) {
            count++;
            curr = curr.next;
        }
        return count;
    }

    public static void main(String[] args) {
        Node first = new Node(210);
        first.next = new Node(220);
        first.next.next = new Node(230);
        int total = countNodes(first);
        System.out.println("Total node count: " + total);
    }
}
