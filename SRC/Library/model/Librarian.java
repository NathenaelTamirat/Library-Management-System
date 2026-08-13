package library.model;

/**
 * Represents a librarian who manages the library system.
 * This class extends Person and demonstrates inheritance and method overriding.
 */
public class Librarian extends Person {
    private String employeeId;
    private String department;
    private boolean isAdmin;
    
    /**
     * Constructor for Librarian.
     * 
     * @param id unique identifier for the librarian
     * @param name full name of the librarian
     * @param email email address of the librarian
     * @param employeeId employee ID of the librarian
     */
    public Librarian(String id, String name, String email, String employeeId) {
        super(id, name, email);
        this.employeeId = employeeId;
        this.isAdmin = false;
        this.department = "General";
    }
    
    /**
     * Constructor for Librarian with department.
     * 
     * @param id unique identifier for the librarian
     * @param name full name of the librarian
     * @param email email address of the librarian
     * @param employeeId employee ID of the librarian
     * @param department department of the librarian
     */
    public Librarian(String id, String name, String email, String employeeId, String department) {
        this(id, name, email, employeeId);
        this.department = department != null ? department : "General";
    }
    
    // Method overriding - implementing abstract methods from Person
    
    /**
     * Gets the role of this librarian.
     * 
     * @return "LIBRARIAN" indicating this is a library staff member
     */
    @Override
    public String getRole() {
        return "LIBRARIAN";
    }
    
    /**
     * Determines if this librarian can borrow books.
     * Librarians typically manage books rather than borrow them.
     * 
     * @return false as librarians don't borrow books in this system
     */
    @Override
    public boolean canBorrowBooks() {
        return false; // Librarians manage books, they don't borrow them
    }
    
    // Librarian-specific methods
    
    /**
     * Gets the employee ID of this librarian.
     * 
     * @return the employee ID
     */
    public String getEmployeeId() {
        return employeeId;
    }
    
    /**
     * Sets the employee ID of this librarian.
     * 
     * @param employeeId the new employee ID
     */
    public void setEmployeeId(String employeeId) {
        if (employeeId == null || employeeId.trim().isEmpty()) {
            throw new IllegalArgumentException("Employee ID cannot be null or empty");
        }
        this.employeeId = employeeId.trim();
    }
    
    /**
     * Gets the department of this librarian.
     * 
     * @return the department name
     */
    public String getDepartment() {
        return department;
    }
    
    /**
     * Sets the department of this librarian.
     * 
     * @param department the new department name
     */
    public void setDepartment(String department) {
        this.department = department != null ? department.trim() : "General";
    }
    
    /**
     * Checks if this librarian has admin privileges.
     * 
     * @return true if librarian is an admin, false otherwise
     */
    public boolean isAdmin() {
        return isAdmin;
    }
    
    /**
     * Sets the admin status of this librarian.
     * 
     * @param admin the new admin status
     */
    public void setAdmin(boolean admin) {
        this.isAdmin = admin;
    }
    
    /**
     * Gets the full title of this librarian including department.
     * 
     * @return formatted title string
     */
    public String getFullTitle() {
        StringBuilder title = new StringBuilder("Librarian");
        
        if (isAdmin) {
            title.append(" (Administrator)");
        }
        
        if (department != null && !department.equals("General")) {
            title.append(" - ").append(department).append(" Department");
        }
        
        return title.toString();
    }
    
    /**
     * Checks if this librarian can perform administrative tasks.
     * 
     * @return true if librarian can perform admin tasks
     */
    public boolean canPerformAdminTasks() {
        return isAdmin;
    }
    
    /**
     * Checks if this librarian can manage users.
     * 
     * @return true if librarian can manage users
     */
    public boolean canManageUsers() {
        return true; // All librarians can manage users
    }
    
    /**
     * Checks if this librarian can manage books.
     * 
     * @return true if librarian can manage books
     */
    public boolean canManageBooks() {
        return true; // All librarians can manage books
    }
    
    /**
     * Checks if this librarian can access reports.
     * 
     * @return true if librarian can access reports
     */
    public boolean canAccessReports() {
        return true; // All librarians can access reports
    }
    
    /**
     * Checks if this librarian can modify system settings.
     * 
     * @return true if librarian can modify system settings
     */
    public boolean canModifySystemSettings() {
        return isAdmin; // Only admin librarians can modify system settings
    }
    
    /**
     * Gets a summary of the librarian's permissions and details.
     * 
     * @return formatted string with librarian information
     */
    public String getLibrarianSummary() {
        StringBuilder summary = new StringBuilder();
        summary.append("Librarian: ").append(getName()).append("\n");
        summary.append("Employee ID: ").append(employeeId).append("\n");
        summary.append("Department: ").append(department).append("\n");
        summary.append("Admin Privileges: ").append(isAdmin ? "Yes" : "No").append("\n");
        summary.append("Email: ").append(getEmail()).append("\n");
        
        if (getPhone() != null && !getPhone().isEmpty()) {
            summary.append("Phone: ").append(getPhone()).append("\n");
        }
        
        summary.append("Permissions:\n");
        summary.append("- Manage Books: Yes\n");
        summary.append("- Manage Users: Yes\n");
        summary.append("- Access Reports: Yes\n");
        summary.append("- System Settings: ").append(isAdmin ? "Yes" : "No");
        
        return summary.toString();
    }
    
    @Override
    public String toString() {
        return String.format("Librarian{id='%s', name='%s', employeeId='%s', department='%s', admin=%s}", 
                           getId(), getName(), employeeId, department, isAdmin);
    }
}