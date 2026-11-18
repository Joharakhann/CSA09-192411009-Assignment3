package Q2;

import java.io.IOException;
import java.util.Scanner;

// Custom Checked Exception 1: Submission past deadline
class ExamTimeoutException extends Exception {
    public ExamTimeoutException(String message) {
        super(message);
    }
}

// Custom Checked Exception 2: Unanswered mandatory questions
class IncompleteSubmissionException extends Exception {
    public IncompleteSubmissionException(String message) {
        super(message);
    }
}

// Custom Checked Exception 3: Malformed answer payload
class InvalidAnswerFormatException extends Exception {
    public InvalidAnswerFormatException(String message) {
        super(message);
    }
}

// Custom Wrapper Exception for Exception Chaining
class ExamPersistenceException extends Exception {
    public ExamPersistenceException(String message, Throwable cause) {
        super(message, cause);
    }
}

// Layer 3: Data Access / Repository Layer
class ExamRepository {
    public void saveSubmission(String studentId, String answers, boolean simulateDiskFailure)
            throws ExamPersistenceException {
        try {
            if (simulateDiskFailure) {
                throw new IOException("Database write timeout on exam_submissions_2026 table.");
            }
            System.out.println("[Repository] Saved answers for Student " + studentId + ": " + answers);
        } catch (IOException ioEx) {
            // Exception Chaining: wrapping low-level IOException inside domain exception
            throw new ExamPersistenceException("Failed to persist exam submission to storage.", ioEx);
        }
    }
}

// Layer 2: Business Logic / Service Layer
class ExamService {
    private final ExamRepository repository = new ExamRepository();

    public void evaluateAndStore(String studentId, int elapsedMinutes, int answeredCount,
                                 int totalQuestions, String answerFormat, boolean simulateDiskFailure)
            throws ExamTimeoutException, IncompleteSubmissionException,
                   InvalidAnswerFormatException, ExamPersistenceException {

        // Rule 1: Time limit check (60 mins max)
        if (elapsedMinutes > 60) {
            throw new ExamTimeoutException(
                "Submission rejected: Elapsed time (" + elapsedMinutes + " mins) exceeded 60-minute limit."
            );
        }

        // Rule 2: Format validation
        if (!answerFormat.matches("([1-5]:[A-D],)*[1-5]:[A-D]")) {
            throw new InvalidAnswerFormatException(
                "Malformed answer string '" + answerFormat + "'. Expected format: 1:A,2:B,..."
            );
        }

        // Rule 3: Completeness check
        if (answeredCount < totalQuestions) {
            throw new IncompleteSubmissionException(
                "Incomplete attempt: Answered " + answeredCount + " of " + totalQuestions + " mandatory questions."
            );
        }

        // Propagates ExamPersistenceException up from Repository
        repository.saveSubmission(studentId, answerFormat, simulateDiskFailure);
    }
}

// Layer 1: Presentation / Controller Layer
public class OnlineExamPortal {
    public static void main(String[] args) {
        Scanner scanner = new Scanner(System.in);
        ExamService examService = new ExamService();

        System.out.println("=== ONLINE EXAMINATION PORTAL (CO3) ===");
        try {
            System.out.print("Enter Student ID: ");
            String studentId = scanner.nextLine().trim();

            System.out.print("Enter Elapsed Time (minutes): ");
            int elapsed = Integer.parseInt(scanner.nextLine().trim());

            System.out.print("Enter Answered Question Count (out of 5): ");
            int count = Integer.parseInt(scanner.nextLine().trim());

            System.out.print("Enter Answer String (e.g., 1:A,2:B,3:C,4:D,5:A): ");
            String answers = scanner.nextLine().trim();

            System.out.print("Simulate Storage Failure? (true/false): ");
            boolean failDisk = Boolean.parseBoolean(scanner.nextLine().trim());

            // Call propagates through Controller -> Service -> Repository
            examService.evaluateAndStore(studentId, elapsed, count, 5, answers, failDisk);
            System.out.println("[Controller] Exam submitted and verified successfully!");

        } catch (ExamTimeoutException e) {
            System.err.println("[HTTP 408 - TIMEOUT] " + e.getMessage());
        } catch (InvalidAnswerFormatException e) {
            System.err.println("[HTTP 400 - BAD FORMAT] " + e.getMessage());
        } catch (IncompleteSubmissionException e) {
            System.err.println("[HTTP 422 - INCOMPLETE] " + e.getMessage());
        } catch (ExamPersistenceException e) {
            System.err.println("[HTTP 500 - STORAGE ERROR] " + e.getMessage());
            System.err.println("   -> Root Cause (getCause()): " + e.getCause());
        } finally {
            scanner.close();
            System.out.println("[Controller] Exam session lock released.");
        }
    }
}