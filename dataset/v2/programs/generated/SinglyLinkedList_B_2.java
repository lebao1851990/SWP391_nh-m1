public class SinglyLinkedList_B_2 {
    static class Node {
        int payload;
        Node nextPointer;
        Node(int payload) {
            this.payload = payload;
            this.nextPointer = null;
        }
    }

    public static int countNodes(Node head) {
        int nodeCounter = 0;
        Node iteratorNode = head;
        while (iteratorNode != null) {
            nodeCounter++;
            iteratorNode = iteratorNode.nextPointer;
        }
        return nodeCounter;
    }

    public static void main(String[] args) {
        Node first = new Node(10);
        first.nextPointer = new Node(20);
        first.nextPointer.nextPointer = new Node(30);
        int total = countNodes(first);
        System.out.println("Total node nodeCounter: " + total);
    }
}
