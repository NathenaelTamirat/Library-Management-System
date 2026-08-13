package library.model;

/**
 * Enumeration representing the different states of a book loan.
 */
public enum LoanStatus {
    /**
     * The loan is currently active - book is borrowed and not yet returned.
     */
    ACTIVE("Active"),
    
    /**
     * The book has been returned successfully.
     */
    RETURNED("Returned"),
    
    /**
     * The loan is overdue - book should have been returned by now.
     */
    OVERDUE("Overdue"),
    
    /**
     * The book is reported as lost.
     */
    LOST("Lost");
    
    private final String displayName;
    
    /**
     * Constructor for LoanStatus enum.
     * 
     * @param displayName the human-readable name for this status
     */
    LoanStatus(String displayName) {
        this.displayName = displayName;
    }
    
    /**
     * Gets the display name for this loan status.
     * 
     * @return the human-readable name
     */
    public String getDisplayName() {
        return displayName;
    }
    
    /**
     * Checks if this status represents an active loan.
     * 
     * @return true if the loan is active or overdue, false otherwise
     */
    public boolean isActive() {
        return this == ACTIVE || this == OVERDUE;
    }
    
    /**
     * Checks if this status represents a completed loan.
     * 
     * @return true if the loan is returned or lost, false otherwise
     */
    public boolean isCompleted() {
        return this == RETURNED || this == LOST;
    }
    
    @Override
    public String toString() {
        return displayName;
    }
}