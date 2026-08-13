package library.model;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a book request made by a user.
 * This class handles the workflow of book borrowing requests and admin approvals.
 */
public class BookRequest {
    
    public enum RequestStatus {
        PENDING("Pending"),
        APPROVED("Approved"),
        REJECTED("Rejected"),
        FULFILLED("Fulfilled"),
        CANCELLED("Cancelled");
        
        private final String displayName;
        
        RequestStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
        
        @Override
        public String toString() {
            return displayName;
        }
    }
    
    private String requestId;
    private String userId;
    private String bookId;
    private LocalDateTime requestDate;
    private LocalDateTime responseDate;
    private RequestStatus status;
    private String requestedBy;
    private String respondedBy;
    private String notes;
    private int priority; // 1 = High, 2 = Medium, 3 = Low
    
    public BookRequest(String requestId, String userId, String bookId, String requestedBy) {
        this.requestId = requestId;
        this.userId = userId;
        this.bookId = bookId;
        this.requestedBy = requestedBy;
        this.requestDate = LocalDateTime.now();
        this.status = RequestStatus.PENDING;
        this.priority = 2; // Default to medium priority
        this.notes = "";
    }
    
    // Getters and Setters
    public String getRequestId() {
        return requestId;
    }
    
    public void setRequestId(String requestId) {
        this.requestId = requestId;
    }
    
    public String getUserId() {
        return userId;
    }
    
    public void setUserId(String userId) {
        this.userId = userId;
    }
    
    public String getBookId() {
        return bookId;
    }
    
    public void setBookId(String bookId) {
        this.bookId = bookId;
    }
    
    public LocalDateTime getRequestDate() {
        return requestDate;
    }
    
    public void setRequestDate(LocalDateTime requestDate) {
        this.requestDate = requestDate;
    }
    
    public LocalDateTime getResponseDate() {
        return responseDate;
    }
    
    public void setResponseDate(LocalDateTime responseDate) {
        this.responseDate = responseDate;
    }
    
    public RequestStatus getStatus() {
        return status;
    }
    
    public void setStatus(RequestStatus status) {
        this.status = status;
        if (status != RequestStatus.PENDING && responseDate == null) {
            this.responseDate = LocalDateTime.now();
        }
    }
    
    public String getRequestedBy() {
        return requestedBy;
    }
    
    public void setRequestedBy(String requestedBy) {
        this.requestedBy = requestedBy;
    }
    
    public String getRespondedBy() {
        return respondedBy;
    }
    
    public void setRespondedBy(String respondedBy) {
        this.respondedBy = respondedBy;
    }
    
    public String getNotes() {
        return notes;
    }
    
    public void setNotes(String notes) {
        this.notes = notes;
    }
    
    public int getPriority() {
        return priority;
    }
    
    public void setPriority(int priority) {
        this.priority = Math.max(1, Math.min(3, priority)); // Ensure priority is between 1-3
    }
    
    public String getPriorityString() {
        switch (priority) {
            case 1: return "High";
            case 2: return "Medium";
            case 3: return "Low";
            default: return "Medium";
        }
    }
    
    /**
     * Approves the book request.
     */
    public void approve(String adminUser, String notes) {
        this.status = RequestStatus.APPROVED;
        this.respondedBy = adminUser;
        this.responseDate = LocalDateTime.now();
        if (notes != null && !notes.trim().isEmpty()) {
            this.notes = notes;
        }
    }
    
    /**
     * Rejects the book request.
     */
    public void reject(String adminUser, String reason) {
        this.status = RequestStatus.REJECTED;
        this.respondedBy = adminUser;
        this.responseDate = LocalDateTime.now();
        this.notes = reason != null ? reason : "Request rejected";
    }
    
    /**
     * Marks the request as fulfilled (book has been issued).
     */
    public void fulfill(String adminUser) {
        this.status = RequestStatus.FULFILLED;
        this.respondedBy = adminUser;
        this.responseDate = LocalDateTime.now();
    }
    
    /**
     * Cancels the book request.
     */
    public void cancel(String user) {
        this.status = RequestStatus.CANCELLED;
        this.respondedBy = user;
        this.responseDate = LocalDateTime.now();
    }
    
    /**
     * Checks if the request is pending.
     */
    public boolean isPending() {
        return status == RequestStatus.PENDING;
    }
    
    /**
     * Checks if the request is approved.
     */
    public boolean isApproved() {
        return status == RequestStatus.APPROVED;
    }
    
    /**
     * Checks if the request is rejected.
     */
    public boolean isRejected() {
        return status == RequestStatus.REJECTED;
    }
    
    /**
     * Gets the formatted request date.
     */
    public String getFormattedRequestDate() {
        return requestDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
    
    /**
     * Gets the formatted response date.
     */
    public String getFormattedResponseDate() {
        if (responseDate == null) {
            return "N/A";
        }
        return responseDate.format(DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm"));
    }
    
    /**
     * Gets a summary of the request.
     */
    public String getRequestSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Request ID: ").append(requestId).append("\n");
        summary.append("User: ").append(requestedBy).append(" (").append(userId).append(")\n");
        summary.append("Book ID: ").append(bookId).append("\n");
        summary.append("Status: ").append(status.getDisplayName()).append("\n");
        summary.append("Priority: ").append(getPriorityString()).append("\n");
        summary.append("Requested: ").append(getFormattedRequestDate()).append("\n");
        
        if (responseDate != null) {
            summary.append("Responded: ").append(getFormattedResponseDate()).append("\n");
            summary.append("Responded by: ").append(respondedBy != null ? respondedBy : "N/A").append("\n");
        }
        
        if (notes != null && !notes.trim().isEmpty()) {
            summary.append("Notes: ").append(notes).append("\n");
        }
        
        return summary.toString();
    }
    
    @Override
    public String toString() {
        return String.format("BookRequest[%s] %s -> %s (%s)", 
                           requestId, requestedBy, bookId, status.getDisplayName());
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        BookRequest that = (BookRequest) obj;
        return requestId != null ? requestId.equals(that.requestId) : that.requestId == null;
    }
    
    @Override
    public int hashCode() {
        return requestId != null ? requestId.hashCode() : 0;
    }
}