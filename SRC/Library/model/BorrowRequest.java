package library.model;

import java.time.LocalDate;

public class BorrowRequest {
    private String requestId;
    private String username;
    private String bookId;
    private LocalDate requestDate;
    private LocalDate returnDate;
    private RequestStatus status;
    
    public enum RequestStatus {
        PENDING("Pending"),
        APPROVED("Approved"),
        DENIED("Denied");
        
        private final String displayName;
        
        RequestStatus(String displayName) {
            this.displayName = displayName;
        }
        
        public String getDisplayName() {
            return displayName;
        }
    }
    
    public BorrowRequest(String requestId, String username, String bookId, LocalDate returnDate) {
        this.requestId = requestId;
        this.username = username;
        this.bookId = bookId;
        this.requestDate = LocalDate.now();
        this.returnDate = returnDate;
        this.status = RequestStatus.PENDING;
    }
    
    public String getRequestId() { return requestId; }
    public String getUsername() { return username; }
    public String getBookId() { return bookId; }
    public LocalDate getRequestDate() { return requestDate; }
    public LocalDate getReturnDate() { return returnDate; }
    public RequestStatus getStatus() { return status; }
    
    public void setStatus(RequestStatus status) { this.status = status; }
}