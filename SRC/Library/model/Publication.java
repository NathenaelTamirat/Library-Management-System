package library.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Abstract base class representing a publication in the library system.
 * This class demonstrates partial abstraction by providing common functionality
 * while requiring subclasses to implement specific behaviors.
 */
public abstract class Publication {
    private String id;
    private String title;
    private String author;
    private int publicationYear;
    private Category category;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    
    /**
     * Constructor for Publication.
     * 
     * @param id unique identifier for the publication
     * @param title title of the publication
     * @param author author of the publication
     */
    public Publication(String id, String title, String author) {
        this.id = id;
        this.title = title;
        this.author = author;
        this.createdAt = LocalDateTime.now();
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Constructor for Publication with publication year.
     * 
     * @param id unique identifier for the publication
     * @param title title of the publication
     * @param author author of the publication
     * @param publicationYear year the publication was published
     */
    public Publication(String id, String title, String author, int publicationYear) {
        this(id, title, author);
        this.publicationYear = publicationYear;
    }
    
    // Abstract methods - must be implemented by subclasses
    
    /**
     * Gets the type of this publication.
     * 
     * @return the publication type (e.g., "BOOK", "MAGAZINE")
     */
    public abstract String getPublicationType();
    
    /**
     * Determines if this publication is available for borrowing.
     * 
     * @return true if available, false otherwise
     */
    public abstract boolean isAvailable();
    
    /**
     * Gets the maximum loan period for this publication type.
     * 
     * @return maximum loan days
     */
    public abstract int getMaxLoanDays();
    
    // Method overloading demonstration
    
    /**
     * Updates the publication information with title only.
     * 
     * @param title the new title
     */
    public void updateInfo(String title) {
        this.title = title;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Updates the publication information with title and author.
     * 
     * @param title the new title
     * @param author the new author
     */
    public void updateInfo(String title, String author) {
        this.title = title;
        this.author = author;
        this.updatedAt = LocalDateTime.now();
    }
    
    /**
     * Updates the publication information with all basic details.
     * 
     * @param title the new title
     * @param author the new author
     * @param publicationYear the new publication year
     */
    public void updateInfo(String title, String author, int publicationYear) {
        this.title = title;
        this.author = author;
        this.publicationYear = publicationYear;
        this.updatedAt = LocalDateTime.now();
    }
    
    // Getters and setters with proper encapsulation
    
    public String getId() {
        return id;
    }
    
    public String getTitle() {
        return title;
    }
    
    public void setTitle(String title) {
        if (title == null || title.trim().isEmpty()) {
            throw new IllegalArgumentException("Title cannot be null or empty");
        }
        this.title = title.trim();
        this.updatedAt = LocalDateTime.now();
    }
    
    public String getAuthor() {
        return author;
    }
    
    public void setAuthor(String author) {
        if (author == null || author.trim().isEmpty()) {
            throw new IllegalArgumentException("Author cannot be null or empty");
        }
        this.author = author.trim();
        this.updatedAt = LocalDateTime.now();
    }
    
    public int getPublicationYear() {
        return publicationYear;
    }
    
    public void setPublicationYear(int publicationYear) {
        int currentYear = LocalDateTime.now().getYear();
        if (publicationYear < 1000 || publicationYear > currentYear + 1) {
            throw new IllegalArgumentException("Invalid publication year: " + publicationYear);
        }
        this.publicationYear = publicationYear;
        this.updatedAt = LocalDateTime.now();
    }
    
    public Category getCategory() {
        return category;
    }
    
    public void setCategory(Category category) {
        this.category = category;
        this.updatedAt = LocalDateTime.now();
    }
    
    public LocalDateTime getCreatedAt() {
        return createdAt;
    }
    
    public LocalDateTime getUpdatedAt() {
        return updatedAt;
    }
    
    /**
     * Gets the category name, or "Uncategorized" if no category is set.
     * 
     * @return the category name
     */
    public String getCategoryName() {
        return category != null ? category.getName() : "Uncategorized";
    }
    
    /**
     * Checks if this publication has a category assigned.
     * 
     * @return true if category is assigned, false otherwise
     */
    public boolean hasCategory() {
        return category != null;
    }
    
    /**
     * Gets the age of this publication in years.
     * 
     * @return age in years, or 0 if publication year is not set
     */
    public int getAgeInYears() {
        if (publicationYear <= 0) {
            return 0;
        }
        return LocalDateTime.now().getYear() - publicationYear;
    }
    
    /**
     * Checks if this is a recent publication (published within last 5 years).
     * 
     * @return true if recent, false otherwise
     */
    public boolean isRecentPublication() {
        return getAgeInYears() <= 5;
    }
    
    /**
     * Gets a formatted display string for this publication.
     * 
     * @return formatted string with publication details
     */
    public String getDisplayInfo() {
        StringBuilder info = new StringBuilder();
        info.append("Title: ").append(title).append("\n");
        info.append("Author: ").append(author).append("\n");
        info.append("Type: ").append(getPublicationType()).append("\n");
        
        if (publicationYear > 0) {
            info.append("Year: ").append(publicationYear).append("\n");
        }
        
        info.append("Category: ").append(getCategoryName()).append("\n");
        info.append("Available: ").append(isAvailable() ? "Yes" : "No");
        
        return info.toString();
    }
    
    /**
     * Gets a short summary of this publication.
     * 
     * @return short summary string
     */
    public String getShortSummary() {
        return String.format("%s by %s (%s)", title, author, getPublicationType());
    }
    
    /**
     * Validates the basic publication data.
     * 
     * @return true if all required fields are valid, false otherwise
     */
    public boolean isValid() {
        return id != null && !id.trim().isEmpty() &&
               title != null && !title.trim().isEmpty() &&
               author != null && !author.trim().isEmpty();
    }
    
    /**
     * Searches for a term in the publication's searchable fields.
     * 
     * @param searchTerm the term to search for
     * @return true if the term is found, false otherwise
     */
    public boolean containsSearchTerm(String searchTerm) {
        if (searchTerm == null || searchTerm.trim().isEmpty()) {
            return false;
        }
        
        String term = searchTerm.toLowerCase();
        return title.toLowerCase().contains(term) ||
               author.toLowerCase().contains(term) ||
               getCategoryName().toLowerCase().contains(term);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Publication that = (Publication) obj;
        return Objects.equals(id, that.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("%s{id='%s', title='%s', author='%s', type='%s'}", 
                           getClass().getSimpleName(), id, title, author, getPublicationType());
    }
}