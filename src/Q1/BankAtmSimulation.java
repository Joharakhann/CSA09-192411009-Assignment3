package Q1;

import java.io.File;
import java.io.FileNotFoundException;
import java.util.Scanner;

// Checked Exception: Authentication failure
class InvalidPinException extends Exception {
    public InvalidPinException(String message) {
        super(message);
    }
}

// Checked Exception: Business rule failure
class InsufficientBalanceException extends Exception {
    public InsufficientBalanceException(String message) {
        super(message);
    }
}

public class BankAtmSimulation {
    private static final String VALID_ACCOUNT = "192411009";
    private static final String CORRECT_PIN = "4321";
    private static double balance = 15000.00;

    // Failure Point 1: Card-reader hardware configuration file check (Checked Exception)
    private static void verifyCardReaderHardware(String configPath) throws FileNotFoundException {
        File readerFile = new File(configPath);
        if (!readerFile.exists()) {
            throw new FileNotFoundException("Card-reader hardware file missing at: " + configPath);
        }
    }

    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        System.out.println("=== SECURE BANK ATM SIMULATION ===");

        try {
            System.out.print("Enter Card-Reader Config Path (type 'skip' to bypass): ");
            String path = scanner.nextLine().trim();
            if (!path.equalsIgnoreCase("skip")) {
                verifyCardReaderHardware(path);
            }

            // Failure Point 2: Account validation (Unchecked Exception)
            System.out.print("Enter Account Number: ");
            String accNo = scanner.nextLine().trim();
            if (!VALID_ACCOUNT.equals(accNo)) {
                throw new IllegalArgumentException("Invalid Account Number entered.");
            }

            // Failure Point 3: PIN verification (Checked Exception)
            System.out.print("Enter 4-digit PIN: ");
            String inputPin = scanner.nextLine().trim();
            if (!CORRECT_PIN.equals(inputPin)) {
                throw new InvalidPinException("Authentication Failed: Incorrect PIN.");
            }

            // Failure Point 4: Non-numeric input (Unchecked NumberFormatException)
            System.out.print("Enter Withdrawal Amount (INR): ");
            String amountStr = scanner.nextLine().trim();
            double amount = Double.parseDouble(amountStr);

            // Failure Point 5: Negative or zero amount (Unchecked IllegalArgumentException)
            if (amount <= 0) {
                throw new IllegalArgumentException("Withdrawal amount must be strictly positive.");
            }

            // Failure Point 6: Overdraft check (Checked InsufficientBalanceException)
            if (amount > balance) {
                throw new InsufficientBalanceException(
                    "Transaction Declined: Requested ₹" + amount + " exceeds current balance ₹" + balance
                );
            }

            balance -= amount;
            System.out.println("Transaction Successful! Dispensing ₹" + amount);
            System.out.println("Remaining Balance: ₹" + balance);

        } catch (FileNotFoundException | InvalidPinException e) {
            // Multi-catch for Checked Hardware & Security Exceptions
            System.err.println("[CHECKED ERROR - HARDWARE/AUTH] " + e.getMessage());
        } catch (InsufficientBalanceException e) {
            // Checked Business Rule Exception
            System.err.println("[CHECKED ERROR - BALANCE] " + e.getMessage());
        } catch (NumberFormatException e) {
            // Unchecked Format Exception (must come BEFORE IllegalArgumentException)
            System.err.println("[UNCHECKED ERROR - FORMAT] Invalid numeric amount: " + e.getMessage());
        } catch (IllegalArgumentException e) {
            // Unchecked Validation Exception
            System.err.println("[UNCHECKED ERROR - VALIDATION] " + e.getMessage());
        } catch (Exception e) {
            // Catch-all safety fallback
            System.err.println("[SYSTEM ERROR] Unexpected failure: " + e.getMessage());
        } finally {
            // Guaranteed cleanup block
            System.out.println("[FINALLY BLOCK] ATM session ended. Card ejected safely.");
            scanner.close();
        }
    }
}