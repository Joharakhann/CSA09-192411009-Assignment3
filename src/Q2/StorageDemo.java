package Q2;

import java.io.IOException;

class StorageException extends Exception {
    public StorageException(String message, Throwable cause) {
        super(message, cause); // Preserves original exception in the cause chain
    }
}

public class StorageDemo {
    public static void writeExamRecord() throws StorageException {
        try {
            // Simulating low-level disk/network I/O failure
            throw new IOException("Disk sector write error (I/O error code 0x05)");
        } catch (IOException e) {
            // Wrap low-level IOException inside high-level StorageException
            throw new StorageException("Unable to store candidate response sheet.", e);
        }
    }

    public static void main(String[] args) {
        try {
            writeExamRecord();
        } catch (StorageException e) {
            System.err.println("Caught High-Level Exception: " + e.getMessage());
            System.err.println("Original Root Cause via getCause(): " + e.getCause());
            System.err.println("\nFull Chained Stack Trace:");
            e.printStackTrace();
        }
    }
}