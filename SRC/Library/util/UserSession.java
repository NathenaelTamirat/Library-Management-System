package library.util;

/**
 * Manages user session information and authentication state.
 * This class maintains the current logged-in user's information and permissions.
 */
public class UserSession {
    
    private static UserSession instance;
    
    private String currentUser;
    private String userRole;
    private boolean isLoggedIn;
    private long loginTime;
    
    private UserSession() {
        reset();
    }
    
    /**
     * Gets the singleton instance of UserSession.
     */
    public static synchronized UserSession getInstance() {
        if (instance == null) {
            instance = new UserSession();
        }
        return instance;
    }
    
    /**
     * Logs in a user with the specified credentials.
     */
    public void login(String username, String role) {
        this.currentUser = username;
        this.userRole = role;
        this.isLoggedIn = true;
        this.loginTime = System.currentTimeMillis();
    }
    
    /**
     * Logs out the current user.
     */
    public void logout() {
        reset();
    }
    
    /**
     * Resets the session to default state.
     */
    private void reset() {
        this.currentUser = null;
        this.userRole = null;
        this.isLoggedIn = false;
        this.loginTime = 0;
    }
    
    /**
     * Checks if a user is currently logged in.
     */
    public boolean isLoggedIn() {
        return isLoggedIn;
    }
    
    /**
     * Gets the current logged-in user.
     */
    public String getCurrentUser() {
        return currentUser;
    }
    
    /**
     * Gets the current user's role.
     */
    public String getUserRole() {
        return userRole;
    }
    
    /**
     * Checks if the current user is an admin.
     */
    public boolean isAdmin() {
        return "ADMIN".equals(userRole);
    }
    
    /**
     * Checks if the current user is a regular user.
     */
    public boolean isUser() {
        return "USER".equals(userRole);
    }
    
    /**
     * Gets the login time.
     */
    public long getLoginTime() {
        return loginTime;
    }
    
    /**
     * Gets session duration in milliseconds.
     */
    public long getSessionDuration() {
        if (!isLoggedIn) {
            return 0;
        }
        return System.currentTimeMillis() - loginTime;
    }
    
    /**
     * Checks if the current user has permission to perform admin operations.
     */
    public boolean hasAdminPermission() {
        return isLoggedIn && isAdmin();
    }
    
    /**
     * Checks if the current user has permission to perform user operations.
     */
    public boolean hasUserPermission() {
        return isLoggedIn && (isAdmin() || isUser());
    }
    
    /**
     * Gets a display string for the current session.
     */
    public String getSessionInfo() {
        if (!isLoggedIn) {
            return "Not logged in";
        }
        
        long duration = getSessionDuration();
        long minutes = duration / (1000 * 60);
        long seconds = (duration / 1000) % 60;
        
        return String.format("User: %s (%s) - Session: %02d:%02d", 
                           currentUser, userRole, minutes, seconds);
    }
}