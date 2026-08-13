package library.model;

import java.util.*;

/**
 * Represents a book category in the library system.
 * Categories are used to organize and classify books.
 */
public class Category implements Comparable<Category> {
    private int id;
    private String name;
    private String description;
    private List<Book> books;
    
    /**
     * Constructor for Category.
     * 
     * @param name the name of the category
     * @param description the description of the category
     */
    public Category(String name, String description) {
        this.name = name;
        this.description = description;
        this.books = new ArrayList<>();
    }
    
    /**
     * Constructor for Category with ID (used when loading from database).
     * 
     * @param id the unique identifier for the category
     * @param name the name of the category
     * @param description the description of the category
     */
    public Category(int id, String name, String description) {
        this(name, description);
        this.id = id;
    }
    
    // Getters and setters
    
    public int getId() {
        return id;
    }
    
    public void setId(int id) {
        this.id = id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Category name cannot be null or empty");
        }
        this.name = name.trim();
    }
    
    public String getDescription() {
        return description;
    }
    
    public void setDescription(String description) {
        this.description = description != null ? description.trim() : "";
    }
    
    /**
     * Gets the list of books in this category.
     * Returns a defensive copy to maintain encapsulation.
     * 
     * @return a copy of the books list
     */
    public List<Book> getBooks() {
        return new ArrayList<>(books);
    }
    
    /**
     * Adds a book to this category.
     * 
     * @param book the book to add
     */
    public void addBook(Book book) {
        if (book == null) {
            throw new IllegalArgumentException("Book cannot be null");
        }
        if (!books.contains(book)) {
            books.add(book);
        }
    }
    
    /**
     * Removes a book from this category.
     * 
     * @param book the book to remove
     * @return true if the book was removed, false if it wasn't in the category
     */
    public boolean removeBook(Book book) {
        return books.remove(book);
    }
    
    /**
     * Gets the number of books in this category.
     * 
     * @return the number of books
     */
    public int getBookCount() {
        return books.size();
    }
    
    /**
     * Checks if this category contains any books.
     * 
     * @return true if category has books, false otherwise
     */
    public boolean hasBooks() {
        return !books.isEmpty();
    }
    
    /**
     * Checks if this category contains a specific book.
     * 
     * @param book the book to check for
     * @return true if the book is in this category, false otherwise
     */
    public boolean containsBook(Book book) {
        return books.contains(book);
    }
    
    /**
     * Gets a summary of this category.
     * 
     * @return formatted string with category information
     */
    public String getCategorySummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Category: ").append(name).append("\n");
        summary.append("Description: ").append(description != null ? description : "No description").append("\n");
        summary.append("Number of Books: ").append(getBookCount());
        
        return summary.toString();
    }
    
    /**
     * Clears all books from this category.
     */
    public void clearBooks() {
        books.clear();
    }
    
    @Override
    public int compareTo(Category other) {
        if (other == null) {
            return 1;
        }
        return this.name.compareToIgnoreCase(other.name);
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Category category = (Category) obj;
        return id == category.id && Objects.equals(name, category.name);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id, name);
    }
    
    @Override
    public String toString() {
        return name;
    }
}