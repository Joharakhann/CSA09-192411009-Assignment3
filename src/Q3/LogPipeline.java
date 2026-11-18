package Q3;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.File;
import java.io.FileReader;
import java.io.FileWriter;
import java.io.IOException;

// Custom AutoCloseable resource simulating a remote audit stream
class AuditConnection implements AutoCloseable {
    private final String endpoint;
    private final boolean failOnClose;

    public AuditConnection(String endpoint, boolean failOnClose) {
        this.endpoint = endpoint;
        this.failOnClose = failOnClose;
        System.out.println("[OPEN] AuditConnection connected to " + endpoint);
    }

    public void logEvent(String record) throws IOException {
        if (record.contains("CORRUPT_PACKET")) {
            throw new IOException("Primary Stream Failure: Corrupted log record encountered -> " + record);
        }
        System.out.println("[AUDIT] Forwarded: " + record);
    }

    @Override
    public void close() throws IOException {
        System.out.println("[CLOSE] Closing AuditConnection (" + endpoint + ")...");
        if (failOnClose) {
            throw new IOException("Close Failure: Timeout while flushing AuditConnection buffer!");
        }
        System.out.println("[CLOSED] AuditConnection closed cleanly.");
    }
}

public class LogPipeline {

    // Helper: Creates sample server.log automatically inside the project folder
    private static void prepareSampleLog(File logFile, boolean includeCorruptLine) throws IOException {
        try (BufferedWriter setupWriter = new BufferedWriter(new FileWriter(logFile))) {
            setupWriter.write("INFO: Server started on port 8080\n");
            setupWriter.write("ERROR: Database connection pool exhausted\n");
            if (includeCorruptLine) {
                setupWriter.write("ERROR: CORRUPT_PACKET_0x99\n");
            }
            setupWriter.write("ERROR: Disk latency threshold exceeded\n");
        }
    }

    // PART A: Legacy try-catch-finally (Demonstrates leaked handles & masked primary exception)
    public static void runLegacyPipeline(File inputFile, File outputFile) {
        System.out.println("\n--- 1. LEGACY TRY-CATCH-FINALLY PIPELINE ---");
        BufferedReader reader = null;
        BufferedWriter writer = null;
        AuditConnection audit = null;

        try {
            reader = new BufferedReader(new FileReader(inputFile));
            writer = new BufferedWriter(new FileWriter(outputFile));
            audit = new AuditConnection("https://audit.saveetha.edu/ingest", true); // Simulated close() failure

            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("ERROR")) {
                    writer.write(line);
                    writer.newLine();
                    audit.logEvent(line);
                }
            }
        } catch (IOException e) {
            System.err.println("[LEGACY CATCH] Caught primary exception: " + e.getMessage());
        } finally {
            // FLAW: If audit.close() throws an exception, writer.close() and reader.close()
            // are skipped (resource leak), AND the primary exception is lost/masked!
            try {
                if (audit != null) audit.close();
                if (writer != null) writer.close(); // Skipped when audit.close() throws!
                if (reader != null) reader.close(); // Skipped when audit.close() throws!
            } catch (IOException closeEx) {
                System.err.println("[LEGACY FINALLY] Close threw exception (leaked remaining handles!): "
                        + closeEx.getMessage());
            }
        }
    }

    // PART B: Complete the Code — Refactored Try-With-Resources (Reverse-order close + Suppressed exceptions)
    public static void runTryWithResourcesPipeline(File inputFile, File outputFile, boolean failOnClose) {
        System.out.println("\n--- 2. TRY-WITH-RESOURCES PIPELINE (failOnClose=" + failOnClose + ") ---");

        try (
            BufferedReader reader = new BufferedReader(new FileReader(inputFile));
            BufferedWriter writer = new BufferedWriter(new FileWriter(outputFile));
            AuditConnection audit = new AuditConnection("https://audit.saveetha.edu/ingest", failOnClose)
        ) {
            String line;
            while ((line = reader.readLine()) != null) {
                if (line.startsWith("ERROR")) {
                    writer.write(line);
                    writer.newLine();
                    audit.logEvent(line);
                }
            }
            System.out.println("[SUCCESS] All error logs filtered and audited.");
        } catch (IOException primaryEx) {
            System.err.println("[PRIMARY EXCEPTION] " + primaryEx.getMessage());

            // Retrieve suppressed exceptions thrown during automatic close()
            Throwable[] suppressed = primaryEx.getSuppressed();
            System.err.println("[SUPPRESSED COUNT] " + suppressed.length);
            for (Throwable t : suppressed) {
                System.err.println("   -> Suppressed Exception via getSuppressed(): " + t);
            }
        }
    }

    public static void main(String[] args) throws IOException {
        File inputLog = new File("server.log");
        File outputLog = new File("filtered_errors.log");

        // Run 1: Clean execution using try-with-resources
        prepareSampleLog(inputLog, false);
        runTryWithResourcesPipeline(inputLog, outputLog, false);

        // Run 2: Corrupt packet + close() failure -> compares legacy leak vs. getSuppressed()
        prepareSampleLog(inputLog, true);
        runLegacyPipeline(inputLog, outputLog);
        runTryWithResourcesPipeline(inputLog, outputLog, true);
    }
}