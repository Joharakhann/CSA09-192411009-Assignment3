package Q5;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantLock;

// Shared tiny CPU work inside check-then-act to expose thread interleaving reliably
class BookingWorkSimulator {
    public static void validateSeatAllocation() {
        double dummy = 0;
        for (int i = 1; i <= 25; i++) {
            dummy += Math.sqrt(i);
        }
        if (dummy < 0) {
            System.out.print("");
        }
    }
}

// Strategy 1: Unsynchronized (Demonstrates Race Condition, Overselling & Lost Updates)
class UnsafeBookingSystem {
    private int availableSeats;
    private int successfulBookings = 0;

    public UnsafeBookingSystem(int initialSeats) {
        this.availableSeats = initialSeats;
    }

    public boolean bookSeat() {
        if (availableSeats > 0) {
            BookingWorkSimulator.validateSeatAllocation();
            availableSeats--;       // Non-atomic read-modify-write
            successfulBookings++;   // Non-atomic counter increment
            return true;
        }
        return false;
    }

    public int getAvailableSeats() { return availableSeats; }
    public int getSuccessfulBookings() { return successfulBookings; }
}

// Strategy 2: Synchronized Method (Coarse-grained Intrinsic Monitor Lock)
class SynchronizedMethodBookingSystem {
    private int availableSeats;
    private int successfulBookings = 0;

    public SynchronizedMethodBookingSystem(int initialSeats) {
        this.availableSeats = initialSeats;
    }

    public synchronized boolean bookSeat() {
        if (availableSeats > 0) {
            BookingWorkSimulator.validateSeatAllocation();
            availableSeats--;
            successfulBookings++;
            return true;
        }
        return false;
    }

    public int getAvailableSeats() { return availableSeats; }
    public int getSuccessfulBookings() { return successfulBookings; }
}

// Strategy 3: Synchronized Block (Fine-grained Critical Section)
class SynchronizedBlockBookingSystem {
    private int availableSeats;
    private int successfulBookings = 0;
    private final Object lock = new Object();

    public SynchronizedBlockBookingSystem(int initialSeats) {
        this.availableSeats = initialSeats;
    }

    public boolean bookSeat() {
        // Non-critical request metadata work runs outside the lock
        int requestToken = Thread.currentThread().hashCode() | 1;
        synchronized (lock) {
            if (availableSeats > 0 && requestToken != 0) {
                BookingWorkSimulator.validateSeatAllocation();
                availableSeats--;
                successfulBookings++;
                return true;
            }
            return false;
        }
    }

    public int getAvailableSeats() { return availableSeats; }
    public int getSuccessfulBookings() { return successfulBookings; }
}

// Strategy 4: Explicit ReentrantLock (with try-finally unlock guarantee)
class ReentrantLockBookingSystem {
    private int availableSeats;
    private int successfulBookings = 0;
    private final ReentrantLock lock = new ReentrantLock();

    public ReentrantLockBookingSystem(int initialSeats) {
        this.availableSeats = initialSeats;
    }

    public boolean bookSeat() {
        lock.lock();
        try {
            if (availableSeats > 0) {
                BookingWorkSimulator.validateSeatAllocation();
                availableSeats--;
                successfulBookings++;
                return true;
            }
            return false;
        } finally {
            lock.unlock(); // Always releases lock even if an exception is thrown
        }
    }

    public int getAvailableSeats() { return availableSeats; }
    public int getSuccessfulBookings() { return successfulBookings; }
}

// Strategy 5: Lock-Free AtomicInteger (Compare-And-Swap / CAS loop)
class AtomicBookingSystem {
    private final AtomicInteger availableSeats;
    private final AtomicInteger successfulBookings = new AtomicInteger(0);

    public AtomicBookingSystem(int initialSeats) {
        this.availableSeats = new AtomicInteger(initialSeats);
    }

    public boolean bookSeat() {
        while (true) {
            int current = availableSeats.get();
            if (current <= 0) {
                return false;
            }
            BookingWorkSimulator.validateSeatAllocation();
            if (availableSeats.compareAndSet(current, current - 1)) {
                successfulBookings.incrementAndGet();
                return true;
            }
        }
    }

    public int getAvailableSeats() { return availableSeats.get(); }
    public int getSuccessfulBookings() { return successfulBookings.get(); }
}

public class TicketBookingBenchmark {
    private static final int TOTAL_SEATS = 5000;
    private static final int NUM_THREADS = 10;
    private static final int REQUESTS_PER_THREAD = 1000; // 10 x 1000 = 10,000 bookings

    private static void runThreads(Runnable bookingTask) throws InterruptedException {
        Thread[] workers = new Thread[NUM_THREADS];
        for (int i = 0; i < NUM_THREADS; i++) {
            workers[i] = new Thread(bookingTask, "Booking-Worker-" + (i + 1));
        }
        for (Thread t : workers) t.start();
        for (Thread t : workers) t.join();
    }

    public static void main(String[] args) throws InterruptedException {
        // JVM JIT Warm-up pass
        SynchronizedMethodBookingSystem warmup = new SynchronizedMethodBookingSystem(1000);
        runThreads(warmup::bookSeat);

        System.out.println("=== CONCURRENT TICKET BOOKING BENCHMARK (10,000 ATTEMPTS / 5,000 SEATS) ===");
        System.out.printf("%-24s | %-11s | %-13s | %-10s | %-12s%n",
                "Strategy", "Booked", "Remaining", "Time (ms)", "Integrity");
        System.out.println("--------------------------------------------------------------------------------");

        // 1. Unsynchronized
        UnsafeBookingSystem unsafe = new UnsafeBookingSystem(TOTAL_SEATS);
        long start = System.nanoTime();
        runThreads(unsafe::bookSeat);
        double unsafeMs = (System.nanoTime() - start) / 1_000_000.0;
        boolean unsafeValid = (unsafe.getSuccessfulBookings() == TOTAL_SEATS && unsafe.getAvailableSeats() == 0);
        System.out.printf("%-24s | %-11d | %-13d | %-10.3f | %-12s%n",
                "1. Unsynchronized", unsafe.getSuccessfulBookings(), unsafe.getAvailableSeats(),
                unsafeMs, unsafeValid ? "PASS" : "RACE CORRUPT");

        // 2. Synchronized Method
        SynchronizedMethodBookingSystem syncMethod = new SynchronizedMethodBookingSystem(TOTAL_SEATS);
        start = System.nanoTime();
        runThreads(syncMethod::bookSeat);
        double syncMethodMs = (System.nanoTime() - start) / 1_000_000.0;
        boolean syncMethodValid = (syncMethod.getSuccessfulBookings() == TOTAL_SEATS && syncMethod.getAvailableSeats() == 0);
        System.out.printf("%-24s | %-11d | %-13d | %-10.3f | %-12s%n",
                "2. Synchronized Method", syncMethod.getSuccessfulBookings(), syncMethod.getAvailableSeats(),
                syncMethodMs, syncMethodValid ? "PASS (SAFE)" : "FAIL");

        // 3. Synchronized Block
        SynchronizedBlockBookingSystem syncBlock = new SynchronizedBlockBookingSystem(TOTAL_SEATS);
        start = System.nanoTime();
        runThreads(syncBlock::bookSeat);
        double syncBlockMs = (System.nanoTime() - start) / 1_000_000.0;
        boolean syncBlockValid = (syncBlock.getSuccessfulBookings() == TOTAL_SEATS && syncBlock.getAvailableSeats() == 0);
        System.out.printf("%-24s | %-11d | %-13d | %-10.3f | %-12s%n",
                "3. Synchronized Block", syncBlock.getSuccessfulBookings(), syncBlock.getAvailableSeats(),
                syncBlockMs, syncBlockValid ? "PASS (SAFE)" : "FAIL");

        // 4. ReentrantLock
        ReentrantLockBookingSystem reentrant = new ReentrantLockBookingSystem(TOTAL_SEATS);
        start = System.nanoTime();
        runThreads(reentrant::bookSeat);
        double reentrantMs = (System.nanoTime() - start) / 1_000_000.0;
        boolean reentrantValid = (reentrant.getSuccessfulBookings() == TOTAL_SEATS && reentrant.getAvailableSeats() == 0);
        System.out.printf("%-24s | %-11d | %-13d | %-10.3f | %-12s%n",
                "4. ReentrantLock", reentrant.getSuccessfulBookings(), reentrant.getAvailableSeats(),
                reentrantMs, reentrantValid ? "PASS (SAFE)" : "FAIL");

        // 5. AtomicInteger (CAS)
        AtomicBookingSystem atomic = new AtomicBookingSystem(TOTAL_SEATS);
        start = System.nanoTime();
        runThreads(atomic::bookSeat);
        double atomicMs = (System.nanoTime() - start) / 1_000_000.0;
        boolean atomicValid = (atomic.getSuccessfulBookings() == TOTAL_SEATS && atomic.getAvailableSeats() == 0);
        System.out.printf("%-24s | %-11d | %-13d | %-10.3f | %-12s%n",
                "5. AtomicInteger (CAS)", atomic.getSuccessfulBookings(), atomic.getAvailableSeats(),
                atomicMs, atomicValid ? "PASS (SAFE)" : "FAIL");
    }
}