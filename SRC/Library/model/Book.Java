package library.model;

import java.util.*;

public class Book extends Publication {
    private String isbn;
    private int totalCopies = 1;
    private int availableCopies = 1;
    private Map<String, Loan> activeLoans = new HashMap<>();
    private Set<String> reservedByUsers = new HashSet<>();
    
    private static final int DEFAULT_MAX_LOAN_DAYS = 14;
    private static final String BOOK_TYPE = "BOOK";
    
    public Book(String id, String title, String author, String isbn) {
        super(id, title, author);
        this.isbn = isbn;
    }
    
    public Book(String id, String title, String author, String isbn, int publicationYear) {
        super(id, title, author, publicationYear);
        this.isbn = isbn;
    }
    
    public Book(String id, String title, String author, String isbn, int publicationYear, 
                int totalCopies, int availableCopies) {
        super(id, title, author, publicationYear);
        this.isbn = isbn;
        this.totalCopies = totalCopies;
        this.availableCopies = availableCopies;
    }
    
    @Override
    public String getPublicationType() {
        return BOOK_TYPE;
    }
    
    @Override
    public boolean isAvailable() {
        return availableCopies > 0;
    }
    
    @Override
    public int getMaxLoanDays() {
        return DEFAULT_MAX_LOAN_DAYS;
    }
    
    public String getIsbn() {
        return isbn;
    }
    
    public void setIsbn(String isbn) {
        if (isbn == null || !isValidIsbn(isbn)) {
            throw new IllegalArgumentException("Invalid ISBN format");
        }
        this.isbn = isbn.trim();
    }
    
    public int getTotalCopies() {
        return totalCopies;
    }
    
    public void setTotalCopies(int totalCopies) {
        if (totalCopies < 0) {
            throw new IllegalArgumentException("Total copies cannot be negative");
        }
        if (totalCopies < (this.totalCopies - availableCopies)) {
            throw new IllegalArgumentException("Cannot set total copies less than borrowed copies");
        }
        
        int difference = totalCopies - this.totalCopies;
        this.totalCopies = totalCopies;
        this.availableCopies = Math.max(0, this.availableCopies + difference);
    }
    
    public int getAvailableCopies() {
        return availableCopies;
    }
    
    public void setAvailableCopies(int availableCopies) {
        if (availableCopies < 0 || availableCopies > totalCopies) {
            throw new IllegalArgumentException("Invalid available copies count");
        }
        this.availableCopies = availableCopies;
    }
    
    public int getBorrowedCopies() {
        return totalCopies - availableCopies;
    }
    
    public boolean borrowCopy(Loan loan) {
        if (!isAvailable() || loan == null) {
            return false;
        }
        
        if (!loan.getBookId().equals(getId())) {
            throw new IllegalArgumentException("Loan book ID does not match this book");
        }
        
        availableCopies--;
        activeLoans.put(loan.getId(), loan);
        return true;
    }
    
    public boolean returnCopy(String loanId) {
        Loan loan = activeLoans.remove(loanId);
        if (loan != null) {
            availableCopies++;
            return true;
        }
        return false;
    }
    
    public void addCopies(int copiesToAdd) {
        if (copiesToAdd <= 0) {
            throw new IllegalArgumentException("Number of copies to add must be positive");
        }
        
        this.totalCopies += copiesToAdd;
        this.availableCopies += copiesToAdd;
    }
    
    public boolean removeCopies(int copiesToRemove) {
        if (copiesToRemove <= 0 || copiesToRemove > availableCopies) {
            return false;
        }
        
        this.totalCopies -= copiesToRemove;
        this.availableCopies -= copiesToRemove;
        return true;
    }
    
    public Map<String, Loan> getActiveLoans() {
        return new HashMap<>(activeLoans);
    }
    
    public Set<String> getReservedByUsers() {
        return new HashSet<>(reservedByUsers);
    }
    
    public boolean addReservation(String userId) {
        if (userId == null || userId.trim().isEmpty()) {
            return false;
        }
        return reservedByUsers.add(userId);
    }
    
    public boolean removeReservation(String userId) {
        return reservedByUsers.remove(userId);
    }
    
    public boolean isReservedBy(String userId) {
        return reservedByUsers.contains(userId);
    }
    
    public int getReservationCount() {
        return reservedByUsers.size();
    }
    
    public boolean isPopular() {
        return !reservedByUsers.isEmpty() || 
               (totalCopies > 1 && availableCopies <= totalCopies * 0.2);
    }
    
    public String getAvailabilityStatus() {
        if (availableCopies == 0) {
            return "Not Available";
        } else if (availableCopies == 1) {
            return "1 copy available";
        } else {
            return availableCopies + " copies available";
        }
    }
    
    public String getBookSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append(getDisplayInfo()).append("\n");
        summary.append("ISBN: ").append(isbn).append("\n");
        summary.append("Copies: ").append(availableCopies).append("/").append(totalCopies).append("\n");
        
        if (!reservedByUsers.isEmpty()) {
            summary.append("Reservations: ").append(reservedByUsers.size()).append("\n");
        }
        
        if (isPopular()) {
            summary.append("Status: Popular Book");
        }
        
        return summary.toString();
    }
    
    private boolean isValidIsbn(String isbn) {
        if (isbn == null) return false;
        
        String cleanIsbn = isbn.replaceAll("[\\s-]", "");
        return cleanIsbn.matches("\\d{10}") || cleanIsbn.matches("\\d{13}");
    }
    
    public void clearReservations() {
        reservedByUsers.clear();
    }
    
    public double getUtilizationRate() {
        return totalCopies == 0 ? 0.0 : (double) getBorrowedCopies() / totalCopies;
    }
    
    @Override
    public String toString() {
        return String.format("Book{id='%s', title='%s', author='%s', isbn='%s', available=%d/%d}", 
                           getId(), getTitle(), getAuthor(), isbn, availableCopies, totalCopies);
    }
}