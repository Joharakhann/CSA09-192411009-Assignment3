package Q7;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

// Custom Checked Exception for malformed log records
class CorruptLogFormatException extends Exception {
    public CorruptLogFormatException(String message) {
        super(message);
    }
}

// Immutable DTO returned by each Callable worker upon completing a file
class FileSummary {
    private final String fileName;
    private final int totalLines;
    private final int errorCount;
    private final int warnCount;
    private final String workerThread;

    public FileSummary(String fileName, int totalLines, int errorCount, int warnCount, String workerThread) {
        this.fileName = fileName;
        this.totalLines = totalLines;
        this.errorCount = errorCount;
        this.warnCount = warnCount;
        this.workerThread = workerThread;
    }

    @Override
    public String toString() {
        return String.format("%-18s | Lines: %3d | ERRORS: %2d | WARNS: %2d | Worker: %s",
                fileName, totalLines, errorCount, warnCount, workerThread);
    }
}

// Callable Task: Processes one log file with try-with-resources and propagates checked failures
class FileProcessorTask implements Callable<FileSummary> {
    private final File logFile;
    private final AtomicInteger globalProcessedCounter;

    public FileProcessorTask(File logFile, AtomicInteger globalProcessedCounter) {
        this.logFile = logFile;
        this.globalProcessedCounter = globalProcessedCounter;
    }

    @Override
    public FileSummary call() throws FileNotFoundException, CorruptLogFormatException, IOException, InterruptedException {
        if (!logFile.exists()) {
            throw new FileNotFoundException("Missing batch input file: " + logFile.getName());
        }

        int lines = 0;
        int errors = 0;
        int warns = 0;

        // Deterministic resource management via try-with-resources
        try (BufferedReader reader = new BufferedReader(new FileReader(logFile))) {
            String line;
            while ((line = reader.readLine()) != null) {
                lines++;
                if (line.contains("CORRUPT_BINARY_PAYLOAD")) {
                    throw new CorruptLogFormatException(
                        "Corrupt record detected at line " + lines + " in " + logFile.getName()
                    );
                }
                if (line.startsWith("ERROR")) {
                    errors++;
                } else if (line.startsWith("WARN")) {
                    warns++;
                }
            }
        }

        // Simulate realistic parsing/analytics CPU + I/O latency per file (30 ms)
        Thread.sleep(30);

        globalProcessedCounter.incrementAndGet();
        return new FileSummary(
            logFile.getName(), lines, errors, warns, Thread.currentThread().getName()
        );
    }
}

public class BatchLogProcessor {

    // Prepares 8 valid files + 1 corrupt file + 1 missing file reference (10 total tasks)
    private static List<File> prepareBatchFiles(File batchDir) throws IOException {
        if (!batchDir.exists()) {
            batchDir.mkdirs();
        }
        List<File> fileBatch = new ArrayList<>();

        // 8 healthy log files
        for (int i = 1; i <= 8; i++) {
            File f = new File(batchDir, "node_0" + i + ".log");
            try (BufferedWriter writer = new BufferedWriter(new FileWriter(f))) {
                writer.write("INFO: Node 0" + i + " heartbeat OK\n");
                writer.write("WARN: High memory utilization (78%)\n");
                writer.write("ERROR: Upstream timeout on shard-" + i + "\n");
                writer.write("INFO: Retry succeeded\n");
            }
            fileBatch.add(f);
        }

        // File 9: Injected corrupt record (triggers CorruptLogFormatException)
        File corruptFile = new File(batchDir, "node_09_corrupt.log");
        try (BufferedWriter writer = new BufferedWriter(new FileWriter(corruptFile))) {
            writer.write("INFO: Node 09 startup\n");
            writer.write("CORRUPT_BINARY_PAYLOAD_0xDEAD\n");
        }
        fileBatch.add(corruptFile);

        // File 10: Non-existent file on disk (triggers FileNotFoundException)
        fileBatch.add(new File(batchDir, "node_10_missing.log"));

        return fileBatch;
    }

    // Executes the entire 10-file batch on a fixed thread pool of size 'threadCount'
    public static double executeBatch(List<File> files, int threadCount, boolean verboseReport)
            throws InterruptedException {
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        AtomicInteger successfulFiles = new AtomicInteger(0);
        List<Future<FileSummary>> futures = new ArrayList<>();

        long startTime = System.nanoTime();

        // Submit all Callable tasks concurrently
        for (File file : files) {
            futures.add(executor.submit(new FileProcessorTask(file, successfulFiles)));
        }

        int failedFiles = 0;
        if (verboseReport) {
            System.out.println("\n--- DETAILED BATCH EXECUTION REPORT (POOL SIZE = " + threadCount + ") ---");
        }

        // Collect results via Future.get() with partial-failure resilience
        for (int i = 0; i < futures.size(); i++) {
            String targetName = files.get(i).getName();
            try {
                FileSummary summary = futures.get(i).get();
                if (verboseReport) {
                    System.out.println("[OK]   " + summary);
                }
            } catch (ExecutionException execEx) {
                failedFiles++;
                Throwable rootCause = execEx.getCause(); // Unwrap underlying task exception
                if (verboseReport) {
                    System.err.println("[FAIL] " + String.format("%-18s", targetName)
                            + " | Unwrapped Cause: " + rootCause.getClass().getSimpleName()
                            + " -> " + rootCause.getMessage());
                }
            }
        }

        // Graceful thread pool shutdown
        executor.shutdown();
        if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
            executor.shutdownNow();
        }

        double elapsedMs = (System.nanoTime() - startTime) / 1_000_000.0;
        if (verboseReport) {
            System.out.printf("Batch Summary: %d Succeeded | %d Failed (Isolated) | Total Time: %.2f ms%n",
                    successfulFiles.get(), failedFiles, elapsedMs);
        }
        return elapsedMs;
    }

    public static void main(String[] args) throws IOException, InterruptedException {
        File batchDir = new File("batch_logs");
        List<File> batchFiles = prepareBatchFiles(batchDir);

        // Warm-up run for JVM JIT stability
        executeBatch(batchFiles, 4, false);

        // Run detailed report with 4 threads showing fault isolation on corrupt & missing files
        executeBatch(batchFiles, 4, true);

        // Scaling Benchmark across 1, 2, 4, and 8 threads
        System.out.println("\n=== THREAD POOL SCALING BENCHMARK (10 FILES: 8 VALID + 2 FAULTY) ===");
        System.out.printf("%-12s | %-15s | %-12s | %-14s | %-15s%n",
                "Pool Size", "Time (ms)", "Speedup", "Efficiency", "Batch Outcome");
        System.out.println("----------------------------------------------------------------------------");

        int[] poolSizes = {1, 2, 4, 8};
        double baselineTime = 0.0;

        for (int threads : poolSizes) {
            double timeMs = executeBatch(batchFiles, threads, false);
            if (threads == 1) {
                baselineTime = timeMs;
            }
            double speedup = baselineTime / timeMs;
            double efficiency = (speedup / threads) * 100.0;

            System.out.printf("%-12d | %-15.2f | %-11.2fx | %-13.1f%% | %-15s%n",
                    threads, timeMs, speedup, efficiency, "8 OK / 2 Caught");
        }
    }
}