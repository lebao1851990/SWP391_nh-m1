public class SinglyLinkedList_B_1 {
    static class Node {
        int itemValue;
        Node successor;
        Node(int itemValue) {
            this.itemValue = itemValue;
            this.successor = null;
        }
    }

    public static int countNodes(Node head) {
        int totalElements = 0;
        Node traverser = head;
        while (traverser != null) {
            totalElements++;
            traverser = traverser.successor;
        }
        return totalElements;
    }

    public static void main(String[] args) {
        Node first = new Node(10);
        first.successor = new Node(20);
        first.successor.successor = new Node(30);
        int total = countNodes(first);
        System.out.println("Total node totalElements: " + total);
    }
}
