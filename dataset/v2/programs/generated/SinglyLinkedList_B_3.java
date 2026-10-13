public class SinglyLinkedList_B_3 {
    static class Node {
        int nodeData;
        Node forwardNode;
        Node(int nodeData) {
            this.nodeData = nodeData;
            this.forwardNode = null;
        }
    }

    public static int countNodes(Node head) {
        int listLength = 0;
        Node walkPtr = head;
        while (walkPtr != null) {
            listLength++;
            walkPtr = walkPtr.forwardNode;
        }
        return listLength;
    }

    public static void main(String[] args) {
        Node first = new Node(10);
        first.forwardNode = new Node(20);
        first.forwardNode.forwardNode = new Node(30);
        int total = countNodes(first);
        System.out.println("Total node listLength: " + total);
    }
}
