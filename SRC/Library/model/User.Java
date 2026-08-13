package library.model;

import java.util.*;

public class User extends Person {
    private boolean isActive = true;
    private List<Loan> loanHistory = new ArrayList<>();
    private Set<String> borrowedBookIds = new HashSet<>();
    private static final int MAX_BOOKS_ALLOWED = 5;
    
    public User(String id, String name, String email) {
        super(id, name, email);
    }
    
    public User(String id, String name, String email, String phone) {
        this(id, name, email);
        setPhone(phone);
    }
    
    @Override
    public String getRole() {
        return "MEMBER";
    }
    
    @Override
    public boolean canBorrowBooks() {
        return isActive && borrowedBookIds.size() < MAX_BOOKS_ALLOWED;
    }
    
    public boolean isActive() {
        return isActive;
    }
    
    public void setActive(boolean active) {
        this.isActive = active;
    }
    
    public List<Loan> getLoanHistory() {
        return new ArrayList<>(loanHistory);
    }
    
    public void addLoan(Loan loan) {
        if (loan == null) {
            throw new IllegalArgumentException("Loan cannot be null");
        }
        if (!loan.getUserId().equals(getId())) {
            throw new IllegalArgumentException("Loan does not belong to this user");
        }
        
        loanHistory.add(loan);
        if (loan.getStatus().isActive()) {
            borrowedBookIds.add(loan.getBookId());
        }
    }
    
    public void returnBook(String bookId) {
        borrowedBookIds.remove(bookId);
    }
    
    public List<Loan> getCurrentLoans() {
        return loanHistory.stream()
            .filter(loan -> loan.getStatus().isActive())
            .collect(ArrayList::new, ArrayList::add, ArrayList::addAll);
    }
    
    public Set<String> getBorrowedBookIds() {
        return new HashSet<>(borrowedBookIds);
    }
    
    public boolean canBorrowMore() {
        return canBorrowBooks() && borrowedBookIds.size() < MAX_BOOKS_ALLOWED;
    }
    
    public int getBorrowedBooksCount() {
        return borrowedBookIds.size();
    }
    
    public static int getMaxBooksAllowed() {
        return MAX_BOOKS_ALLOWED;
    }
    
    public boolean hasOverdueLoans() {
        return loanHistory.stream().anyMatch(loan -> loan.getStatus() == LoanStatus.OVERDUE);
    }
    
    public int getTotalLoansCount() {
        return loanHistory.size();
    }
    
    public double getTotalFineAmount() {
        return loanHistory.stream().mapToDouble(Loan::getFineAmount).sum();
    }
    
    public String getBorrowingSummary() {
        return String.format("User: %s\nTotal Loans: %d\nCurrently Borrowed: %d\nTotal Fines: $%.2f\nStatus: %s%s",
            getName(), getTotalLoansCount(), getBorrowedBooksCount(), getTotalFineAmount(),
            isActive ? "Active" : "Inactive", hasOverdueLoans() ? " (Has Overdue Books)" : "");
    }
    
    @Override
    public String toString() {
        return String.format("User{id='%s', name='%s', email='%s', active=%s, borrowed=%d/%d}", 
                           getId(), getName(), getEmail(), isActive, 
                           borrowedBookIds.size(), MAX_BOOKS_ALLOWED);
    }
}