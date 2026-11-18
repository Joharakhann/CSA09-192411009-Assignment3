package Q4;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

// Method 1: Creating a Thread by extending java.lang.Thread (High-Priority VIP Dispatch)
class VipDispatchThread extends Thread {
    private final String riderName;
    private final List<String> completionLog;

    public VipDispatchThread(String riderName, List<String> completionLog) {
        super("VIP-Dispatch-" + riderName);
        this.riderName = riderName;
        this.completionLog = completionLog;
    }

    @Override
    public void run() {
        try {
            // Simulates driver matching and route calculation (enters TIMED_WAITING)
            Thread.sleep(30);
            String entry = getName() + " [Priority=" + getPriority() + ", Thread=" + Thread.currentThread().getName() + "]";
            if (completionLog != null) {
                completionLog.add(entry);
            } else {
                System.out.println("   -> Completed: " + entry);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            System.err.println(getName() + " interrupted.");
        }
    }
}

// Method 2: Creating a Thread by implementing Runnable (Standard / Economy Ride Dispatch)
class StandardDispatchTask implements Runnable {
    private final String riderName;
    private final List<String> completionLog;

    public StandardDispatchTask(String riderName, List<String> completionLog) {
        this.riderName = riderName;
        this.completionLog = completionLog;
    }

    @Override
    public void run() {
        try {
            Thread.sleep(30);
            Thread current = Thread.currentThread();
            String entry = current.getName() + " [Priority=" + current.getPriority() + ", Rider=" + riderName + "]";
            if (completionLog != null) {
                completionLog.add(entry);
            } else {
                System.out.println("   -> Completed: " + entry);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }
}

// Daemon Thread Task: Background GPS Telemetry & Heartbeat Logger
class GpsTelemetryDaemon implements Runnable {
    @Override
    public void run() {
        int ping = 1;
        try {
            while (true) {
                System.out.println("[DAEMON GPS-TELEMETRY] Heartbeat ping #" + ping++ + " (isDaemon=" + Thread.currentThread().isDaemon() + ")");
                Thread.sleep(45);
            }
        } catch (InterruptedException e) {
            // Daemon exits silently when JVM shuts down user threads
        }
    }
}

public class RideDispatchSystem {

    // Experiment 1: Thread Life-Cycle States & run() vs start()
    public static void demonstrateLifecycleAndRunVsStart() throws InterruptedException {
        System.out.println("=== 1. THREAD LIFE CYCLE & run() vs. start() ===");

        // Direct run() call executes synchronously on the 'main' thread (NO new thread spawned)
        Thread directCallTest = new Thread(new StandardDispatchTask("DirectCall-R0", null), "Worker-Direct");
        System.out.println("Calling directCallTest.run() directly:");
        directCallTest.run(); // Prints Thread = main!

        // Proper start() call transitions through NEW -> RUNNABLE -> TIMED_WAITING -> TERMINATED
        VipDispatchThread lifecycleWorker = new VipDispatchThread("Aarav", null);
        System.out.println("\nState right after instantiation : " + lifecycleWorker.getState()); // NEW

        lifecycleWorker.start();
        System.out.println("State immediately after start() : " + lifecycleWorker.getState()); // RUNNABLE

        Thread.sleep(10); // Give worker time to enter Thread.sleep(30)
        System.out.println("State during Thread.sleep(30)   : " + lifecycleWorker.getState()); // TIMED_WAITING

        lifecycleWorker.join(); // Wait for completion
        System.out.println("State after join() completes    : " + lifecycleWorker.getState()); // TERMINATED
    }

    // Experiment 2: 10 Runs of Non-Deterministic Finish Order & Priority Hints
    public static void runNonDeterministicBenchmark() throws InterruptedException {
        System.out.println("\n=== 2. 10-RUN SCHEDULING & PRIORITY EXPERIMENT ===");
        System.out.println("Run# | Finish Order (First -> Last)");
        System.out.println("-------------------------------------------------------------------------");

        for (int run = 1; run <= 10; run++) {
            List<String> finishOrder = Collections.synchronizedList(new ArrayList<>());

            Thread tLow = new Thread(new StandardDispatchTask("PromoUser", finishOrder), "Promo-LOW");
            Thread tNorm = new Thread(new StandardDispatchTask("RegularUser", finishOrder), "Std-NORM");
            VipDispatchThread tHigh = new VipDispatchThread("EmergencyVIP", finishOrder);

            tLow.setPriority(Thread.MIN_PRIORITY);   // Priority 1
            tNorm.setPriority(Thread.NORM_PRIORITY); // Priority 5
            tHigh.setPriority(Thread.MAX_PRIORITY);  // Priority 10

            tLow.start();
            tNorm.start();
            tHigh.start();

            tLow.join();
            tNorm.join();
            tHigh.join();

            // Extract short thread names in exact completion order
            List<String> shortNames = new ArrayList<>();
            for (String s : finishOrder) {
                shortNames.add(s.substring(0, s.indexOf(' ')));
            }
            System.out.printf("Run %02d | %s%n", run, String.join(" -> ", shortNames));
        }
    }

    // Experiment 3: Daemon Thread Lifecycle Verification
    public static void demonstrateDaemonThread() throws InterruptedException {
        System.out.println("\n=== 3. DAEMON THREAD TERMINATION EXPERIMENT ===");
        Thread gpsDaemon = new Thread(new GpsTelemetryDaemon(), "GPS-Telemetry-Daemon");
        gpsDaemon.setDaemon(true); // Must be set BEFORE start()
        gpsDaemon.start();

        Thread userRide = new Thread(new StandardDispatchTask("FinalUserRide", null), "User-Ride-Thread");
        userRide.start();
        userRide.join();

        System.out.println("[MAIN] All non-daemon user threads completed. JVM will now exit and terminate the infinite Daemon thread.");
    }

    public static void main(String[] args) throws InterruptedException {
        demonstrateLifecycleAndRunVsStart();
        runNonDeterministicBenchmark();
        demonstrateDaemonThread();
    }
}