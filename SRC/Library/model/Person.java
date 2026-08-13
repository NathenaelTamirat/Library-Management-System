package library.model;

import java.time.LocalDateTime;
import java.util.Objects;

/**
 * Abstract base class representing a person in the library system.
 * This class demonstrates partial abstraction by providing common functionality
 * while requiring subclasses to implement specific behaviors.
 */
public abstract class Person {
    private String id;
    private String name;
    private String email;
    private String phone;
    private LocalDateTime registrationDate;
    
    /**
     * Constructor for Person.
     * 
     * @param id unique identifier for the person
     * @param name full name of the person
     * @param email email address of the person
     */
    public Person(String id, String name, String email) {
        this.id = id;
        this.name = name;
        this.email = email;
        this.registrationDate = LocalDateTime.now();
    }
    
    // Abstract methods - must be implemented by subclasses
    
    /**
     * Gets the role of this person in the library system.
     * 
     * @return the role (e.g., "MEMBER", "LIBRARIAN")
     */
    public abstract String getRole();
    
    /**
     * Determines if this person can borrow books.
     * 
     * @return true if the person can borrow books, false otherwise
     */
    public abstract boolean canBorrowBooks();
    
    // Method overloading demonstration
    
    /**
     * Updates the contact information with just email.
     * 
     * @param email the new email address
     */
    public void updateContact(String email) {
        this.email = email;
    }
    
    /**
     * Updates the contact information with email and phone.
     * 
     * @param email the new email address
     * @param phone the new phone number
     */
    public void updateContact(String email, String phone) {
        this.email = email;
        this.phone = phone;
    }
    
    /**
     * Updates the contact information with all details.
     * 
     * @param email the new email address
     * @param phone the new phone number
     * @param name the new name
     */
    public void updateContact(String email, String phone, String name) {
        this.email = email;
        this.phone = phone;
        this.name = name;
    }
    
    // Getters and setters with proper encapsulation
    
    public String getId() {
        return id;
    }
    
    public String getName() {
        return name;
    }
    
    public void setName(String name) {
        if (name == null || name.trim().isEmpty()) {
            throw new IllegalArgumentException("Name cannot be null or empty");
        }
        this.name = name.trim();
    }
    
    public String getEmail() {
        return email;
    }
    
    public void setEmail(String email) {
        if (email == null || !isValidEmail(email)) {
            throw new IllegalArgumentException("Invalid email address");
        }
        this.email = email.toLowerCase().trim();
    }
    
    public String getPhone() {
        return phone;
    }
    
    public void setPhone(String phone) {
        this.phone = phone != null ? phone.trim() : null;
    }
    
    public LocalDateTime getRegistrationDate() {
        return registrationDate;
    }
    
    /**
     * Validates email format using a simple regex pattern.
     * 
     * @param email the email to validate
     * @return true if email format is valid, false otherwise
     */
    private boolean isValidEmail(String email) {
        if (email == null || email.trim().isEmpty()) {
            return false;
        }
        String emailRegex = "^[A-Za-z0-9+_.-]+@[A-Za-z0-9.-]+\\.[A-Za-z]{2,}$";
        return email.matches(emailRegex);
    }
    
    /**
     * Gets a formatted display string for this person.
     * 
     * @return formatted string with person details
     */
    public String getDisplayInfo() {
        StringBuilder info = new StringBuilder();
        info.append("ID: ").append(id)
            .append(", Name: ").append(name)
            .append(", Email: ").append(email)
            .append(", Role: ").append(getRole());
        
        if (phone != null && !phone.isEmpty()) {
            info.append(", Phone: ").append(phone);
        }
        
        return info.toString();
    }
    
    @Override
    public boolean equals(Object obj) {
        if (this == obj) return true;
        if (obj == null || getClass() != obj.getClass()) return false;
        Person person = (Person) obj;
        return Objects.equals(id, person.id);
    }
    
    @Override
    public int hashCode() {
        return Objects.hash(id);
    }
    
    @Override
    public String toString() {
        return String.format("%s{id='%s', name='%s', email='%s', role='%s'}", 
                           getClass().getSimpleName(), id, name, email, getRole());
    }
}