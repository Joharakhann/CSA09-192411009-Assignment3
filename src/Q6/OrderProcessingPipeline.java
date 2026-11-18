package Q6;

import java.lang.management.ManagementFactory;
import java.lang.management.ThreadInfo;
import java.lang.management.ThreadMXBean;
import java.util.LinkedList;
import java.util.Queue;

// Bounded Buffer for Producer-Consumer Order Coordination
class BoundedOrderBuffer {
    private final Queue<String> orderQueue = new LinkedList<>();
    private final int capacity;

    public BoundedOrderBuffer(int capacity) {
        this.capacity = capacity;
    }

    public synchronized void produce(String orderId, String producerName) throws InterruptedException {
        // Must use 'while' (not 'if') to guard against spurious wakeups and multi-producer races
        while (orderQueue.size() == capacity) {
            System.out.println("[" + producerName + "] Buffer FULL (size=" + capacity + "). Calling wait()...");
            wait();
        }
        orderQueue.add(orderId);
        System.out.println("[" + producerName + "] Produced -> " + orderId + " | Queue Size: " + orderQueue.size());
        notifyAll(); // Wake up all waiting consumers
    }

    public synchronized String consume(String consumerName) throws InterruptedException {
        // Must use 'while' (not 'if') so re-awakened consumers re-verify queue is non-empty
        while (orderQueue.isEmpty()) {
            System.out.println("[" + consumerName + "] Buffer EMPTY. Calling wait()...");
            wait();
        }
        String orderId = orderQueue.poll();
        System.out.println("[" + consumerName + "] Consumed <- " + orderId + " | Queue Size: " + orderQueue.size());
        notifyAll(); // Wake up all waiting producers
        return orderId;
    }
}

public class OrderProcessingPipeline {

    // PART 1: Deadlock Simulation (Opposite Lock Acquisition Order)
    public static void simulateAndDetectDeadlock() throws InterruptedException {
        System.out.println("=== 1. DEADLOCK SIMULATION & THREADMXBEAN DETECTION ===");
        final Object paymentLock = new Object();
        final Object inventoryLock = new Object();

        Thread t1 = new Thread(() -> {
            synchronized (paymentLock) {
                System.out.println("[Payment-Service-T1] Locked PaymentGateway; requesting InventoryDB...");
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                synchronized (inventoryLock) {
                    System.out.println("[Payment-Service-T1] Locked InventoryDB.");
                }
            }
        }, "Payment-Service-T1");

        Thread t2 = new Thread(() -> {
            synchronized (inventoryLock) {
                System.out.println("[Inventory-Service-T2] Locked InventoryDB; requesting PaymentGateway...");
                try { Thread.sleep(50); } catch (InterruptedException ignored) {}
                synchronized (paymentLock) {
                    System.out.println("[Inventory-Service-T2] Locked PaymentGateway.");
                }
            }
        }, "Inventory-Service-T2");

        // Daemon flag allows the JVM to exit cleanly after we inspect the deadlocked threads
        t1.setDaemon(true);
        t2.setDaemon(true);
        t1.start();
        t2.start();

        Thread.sleep(200); // Wait for both threads to enter circular BLOCKED state

        ThreadMXBean mxBean = ManagementFactory.getThreadMXBean();
        long[] deadlockedIds = mxBean.findDeadlockedThreads();
        if (deadlockedIds != null) {
            System.err.println("[DEADLOCK DETECTED] JVM identified " + deadlockedIds.length + " deadlocked threads:");
            for (ThreadInfo info : mxBean.getThreadInfo(deadlockedIds)) {
                System.err.println("   -> Thread: " + info.getThreadName()
                        + " | State: " + info.getThreadState()
                        + " | Waiting on Lock: " + info.getLockName()
                        + " | Held by: " + info.getLockOwnerName());
            }
        }
    }

    // PART 2: Deadlock Prevention via Strict Global Lock Ordering (Breaks Circular Wait)
    public static void runDeadlockFreeCheckout() throws InterruptedException {
        System.out.println("\n=== 2. DEADLOCK PREVENTION (GLOBAL LOCK ORDERING: Payment -> Inventory) ===");
        final Object paymentLock = new Object();
        final Object inventoryLock = new Object();

        Runnable safeCheckoutTask = () -> {
            String name = Thread.currentThread().getName();
            // Both threads acquire paymentLock FIRST, then inventoryLock SECOND
            synchronized (paymentLock) {
                System.out.println("[" + name + "] Step 1: Acquired PaymentGateway lock.");
                try { Thread.sleep(30); } catch (InterruptedException ignored) {}
                synchronized (inventoryLock) {
                    System.out.println("[" + name + "] Step 2: Acquired InventoryDB lock. Checkout committed!");
                }
            }
        };

        Thread s1 = new Thread(safeCheckoutTask, "Safe-Checkout-T1");
        Thread s2 = new Thread(safeCheckoutTask, "Safe-Checkout-T2");
        s1.start();
        s2.start();
        s1.join();
        s2.join();
        System.out.println("[SUCCESS] Both checkout threads completed with zero deadlock.");
    }

    // PART 3: Complete the Code — Producer-Consumer Coordination (wait / notifyAll)
    public static void runProducerConsumerPipeline() throws InterruptedException {
        System.out.println("\n=== 3. PRODUCER-CONSUMER BOUNDED BUFFER (CAPACITY = 3) ===");
        BoundedOrderBuffer buffer = new BoundedOrderBuffer(3);

        // 2 Producers x 5 orders each = 10 total orders
        Thread p1 = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    buffer.produce("ORD-A" + i, "Producer-1");
                    Thread.sleep(20);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Producer-1");

        Thread p2 = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    buffer.produce("ORD-B" + i, "Producer-2");
                    Thread.sleep(25);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Producer-2");

        // 2 Consumers x 5 orders each = 10 total orders consumed
        Thread c1 = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    buffer.consume("Consumer-1");
                    Thread.sleep(45);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Consumer-1");

        Thread c2 = new Thread(() -> {
            try {
                for (int i = 1; i <= 5; i++) {
                    buffer.consume("Consumer-2");
                    Thread.sleep(50);
                }
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }, "Consumer-2");

        p1.start();
        p2.start();
        c1.start();
        c2.start();

        p1.join();
        p2.join();
        c1.join();
        c2.join();
        System.out.println("[COMPLETE] All 10 orders produced and fulfilled cleanly without overflow or underflow.");
    }

    public static void main(String[] args) throws InterruptedException {
        simulateAndDetectDeadlock();
        runDeadlockFreeCheckout();
        runProducerConsumerPipeline();
    }
}