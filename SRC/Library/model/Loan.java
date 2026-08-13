package library.model;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;

/**
 * Represents a book loan in the library system.
 * This class manages the borrowing relationship between users and books.
 */
public class Loan {
    private String id;
    private String userId;
    private String bookId;
    private LocalDateTime loanDate;
    private LocalDateTime dueDate;
    private LocalDateTime returnDate;
    private LoanStatus status;
    private double fineAmount;
    private String notes;
    
    // Constants for loan management
    private static final double FINE_PER_DAY = 0.50;
    private static final int DEFAULT_LOAN_DAYS = 14;
    
    /**
     * Constructor for creating a new loan.
     * 
     * @param id unique identifier for the loan
     * @param userId ID of the user borrowing the book
     * @param bookId ID of the book being borrowed
     */
    public Loan(String id, String userId, String bookId) {
        this.id = id;
        this.userId = userId;
        this.bookId = bookId;
        this.loanDate = LocalDateTime.now();
        this.dueDate = loanDate.plusDays(DEFAULT_LOAN_DAYS);
        this.status = LoanStatus.ACTIVE;
        this.fineAmount = 0.0;
    }
    
    /**
     * Constructor for creating a loan with custom due date.
     * 
     * @param id unique identifier for the loan
     * @param userId ID of the user borrowing the book
     * @param bookId ID of the book being borrowed
     * @param dueDate the due date for returning the book
     */
    public Loan(String id, String userId, String bookId, LocalDateTime dueDate) {
        this.id = id;
        this.userId = userId;
        this.bookId = bookId;
        this.loanDate = LocalDateTime.now();
        this.dueDate = dueDate;
        this.status = LoanStatus.ACTIVE;
        this.fineAmount = 0.0;
    }
    
    /**
     * Full constructor (typically used when loading from database).
     * 
     * @param id unique identifier for the loan
     * @param userId ID of the user borrowing the book
     * @param bookId ID of the book being borrowed
     * @param loanDate date when the book was borrowed
     * @param dueDate due date for returning the book
     * @param returnDate date when the book was returned (null if not returned)
     * @param status current status of the loan
     * @param fineAmount fine amount for overdue books
     */
    public Loan(String id, String userId, String bookId, LocalDateTime loanDate, 
                LocalDateTime dueDate, LocalDateTime returnDate, LoanStatus status, double fineAmount) {
        this.id = id;
        this.userId = userId;
        this.bookId = bookId;
        this.loanDate = loanDate;
        this.dueDate = dueDate;
        this.returnDate = returnDate;
        this.status = status;
        this.fineAmount = fineAmount;
    }
    
    // Getters and setters
    
    public String getId() {
        return id;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public String getBookId() {
        return bookId;
    }
    
    public LocalDateTime getLoanDate() {
        return loanDate;
    }
    
    public LocalDateTime getDueDate() {
        return dueDate;
    }
    
    public void setDueDate(LocalDateTime dueDate) {
        if (dueDate == null || dueDate.isBefore(loanDate)) {
            throw new IllegalArgumentException("Due date cannot be null or before loan date");
        }
        this.dueDate = dueDate;
    }
    
    public LocalDateTime getReturnDate() {
        return returnDate;
    }
    
    public LoanStatus getStatus() {
        return status;
    }
    
    public void setStatus(LoanStatus status) {
        if (status == null) {
            throw new IllegalArgumentException("Status cannot be null");
        }
        this.status = status;
    }
    
    public double getFineAmount() {
        return fineAmount;
    }
    
    public void setFineAmount(double fineAmount) {
        if (fineAmount < 0) {
            throw new IllegalArgumentException("Fine amount cannot be negative");
        }
        this.fineAmount = fineAmount;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    // Business logic methods
    
    /**
     * Returns the book and updates the loan status.
     * 
     * @return true if the book was successfully returned, false if already returned
     */
    public boolean returnBook() {
        if (status == LoanStatus.RETURNED) {
            return false; // Already returned
        }
        
        this.returnDate = LocalDateTime.now();
        this.status = LoanStatus.RETURNED;
        
        // Calculate fine if overdue
        if (isOverdue()) {
            calculateFine();
        }
        
        return true;
    }
    
    /**
     * Calculates the fine amount for overdue books.
     * 
     * @return the calculated fine amount
     */
    public double calculateFine() {
        if (!isOverdue()) {
            this.fineAmount = 0.0;
            return 0.0;
        }
        
        LocalDateTime referenceDate = returnDate != null ? returnDate : LocalDateTime.now();
        long overdueDays = ChronoUnit.DAYS.between(dueDate, referenceDate);
        
        if (overdueDays > 0) {
            this.fineAmount = overdueDays * FINE_PER_DAY;
        } else {
            this.fineAmount = 0.0;
        }
        
        return this.fineAmount;
    }
    
    /**
     * Checks if the loan is overdue.
     * 
     * @return true if the loan is overdue, false otherwise
     */
    public boolean isOverdue() {
        if (status == LoanStatus.RETURNED) {
            return false; // Can't be overdue if already returned
        }
        
        LocalDateTime now = LocalDateTime.now();
        return now.isAfter(dueDate);
    }
    
    /**
     * Gets the number of days until the book is due.
     * 
     * @return number of days (negative if overdue)
     */
    public long getDaysUntilDue() {
        LocalDateTime now = LocalDateTime.now();
        return ChronoUnit.DAYS.between(now, dueDate);
    }
    
    /**
     * Gets the number of days the book has been borrowed.
     * 
     * @return number of days since loan date
     */
    public long getDaysBorrowed() {
        LocalDateTime referenceDate = returnDate != null ? returnDate : LocalDateTime.now();
        return ChronoUnit.DAYS.between(loanDate, referenceDate);
    }
    
    /**
     * Extends the due date by the specified number of days.
     * 
     * @param days number of days to extend
     * @return true if extension was successful, false if loan is already returned
     */
    public boolean extendDueDate(int days) {
        if (status == LoanStatus.RETURNED || days <= 0) {
            return false;
        }
        
        this.dueDate = dueDate.plusDays(days);
        
        // Update status if no longer overdue
        if (status == LoanStatus.OVERDUE && !isOverdue()) {
            this.status = LoanStatus.ACTIVE;
        }
        
        return true;
    }
    
    /**
     * Updates the loan status based on current conditions.
     */
    public void updateStatus() {
        if (status == LoanStatus.RETURNED || status == LoanStatus.LOST) {
            return; // Don't change final statuses
        }
        
        if (isOverdue()) {
            this.status = LoanStatus.OVERDUE;
            calculateFine();
        } else {
            this.status = LoanStatus.ACTIVE;
        }
    }
    
    /**
     * Marks the book as lost.
     * 
     * @param lostFine the fine amount for the lost book
     */
    public void markAsLost(double lostFine) {
        this.status = LoanStatus.LOST;
        this.fineAmount = lostFine;
        this.returnDate = LocalDateTime.now();
    }
    
    /**
     * Gets a summary of the loan information.
     * 
     * @return formatted string with loan details
     */
    public String getLoanSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Loan ID: ").append(id).append("\n");
        summary.append("User ID: ").append(userId).append("\n");
        summary.append("Book ID: ").append(bookId).append("\n");
        summary.append("Loan Date: ").append(loanDate.toLocalDate()).append("\n");
        summary.append("Due Date: ").append(dueDate.toLocalDate()).append("\n");
        summary.append("Status: ").append(status.getDisplayName()).append("\n");
        
        if (returnDate != null) {
            summary.append("Return Date: ").append(returnDate.toLocalDate()).append("\n");
        }
        
        if (fineAmount > 0) {
            summary.append("Fine Amount: $").append(String.format("%.2f", fineAmount)).append("\n");
        }
        
        if (isOverdue() && status != LoanStatus.RETURNED) {
            summary.append("Days Overdue: ").append(-getDaysUntilDue()).append("\n");
        } else if (status == LoanStatus.ACTIVE) {
            summary.append("Days Until Due: ").append(getDaysUntilDue()).append("\n");
        }
        
        if (notes != null && !notes.isEmpty()) {
            summary.append("Notes: ").append(notes);
        }
        
        return summary.toString();
    }
    
    /**
     * Gets the default loan period in days.
     * 
     * @return default loan days
     */
    public static int getDefaultLoanDays() {
        return DEFAULT_LOAN_DAYS;
    }
    
    /**
     * Gets the fine amount per day.
     * 
     * @return fine per day
     */
    public static double getFinePerDay() {
        return FINE_PER_DAY;
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Loan loan = (Loan) obj;
        return Objects.equals(id, loan.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("Loan{id='%s', userId='%s', bookId='%s', status=%s, dueDate=%s}", 
                           id, userId, bookId, status, dueDate.toLocalDate());
    }
}