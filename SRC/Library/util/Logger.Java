package library.util;

import java.io.*;
import java.nio.file.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

public class Logger {
    private static final String LOG_DIRECTORY = "logs";
    private static final String LOG_FILE_PATTERN = "library-%s.log";
    private static final DateTimeFormatter DATE_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter TIMESTAMP_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");
    
    private static Logger instance;
    private String logLevel;
    private Path logDirectory;
    
    public enum LogLevel {
        DEBUG, INFO, WARNING, ERROR
    }
    
    private Logger() {
        this.logLevel = "INFO";
        this.logDirectory = Paths.get(LOG_DIRECTORY);
        initializeLogDirectory();
    }
    
    public static synchronized Logger getInstance() {
        if (instance == null) {
            instance = new Logger();
        }
        return instance;
    }
    
    private void initializeLogDirectory() {
        try {
            if (!Files.exists(logDirectory)) {
                Files.createDirectories(logDirectory);
            }
        } catch (IOException e) {
            System.err.println("Failed to create log directory: " + e.getMessage());
        }
    }
    
    public void setLogLevel(String level) {
        this.logLevel = level != null ? level.toUpperCase() : "INFO";
    }
    
    public String getLogLevel() {
        return logLevel;
    }
    
    public void logInfo(String message) {
        log(LogLevel.INFO, message, null);
    }
    
    public void logError(String message) {
        log(LogLevel.ERROR, message, null);
    }
    
    public void logError(String message, Exception exception) {
        log(LogLevel.ERROR, message, exception);
    }
    
    public void logWarning(String message) {
        log(LogLevel.WARNING, message, null);
    }
    
    public void logDebug(String message) {
        log(LogLevel.DEBUG, message, null);
    }
    
    private void log(LogLevel level, String message, Exception exception) {
        if (!shouldLog(level)) return;
        
        String timestamp = LocalDateTime.now().format(TIMESTAMP_FORMAT);
        String logEntry = String.format("[%s] %s: %s", timestamp, level, message);
        
        System.out.println(logEntry);
        
        String logFileName = String.format(LOG_FILE_PATTERN, LocalDateTime.now().format(DATE_FORMAT));
        Path logFile = logDirectory.resolve(logFileName);
        
        try (BufferedWriter writer = Files.newBufferedWriter(logFile, 
                StandardOpenOption.CREATE, StandardOpenOption.APPEND)) {
            
            writer.write(logEntry);
            writer.newLine();
            
            if (exception != null) {
                writer.write("Exception: " + exception.getClass().getSimpleName() + 
                           " - " + exception.getMessage());
                writer.newLine();
                
                StringWriter sw = new StringWriter();
                PrintWriter pw = new PrintWriter(sw);
                exception.printStackTrace(pw);
                writer.write("Stack trace:");
                writer.newLine();
                writer.write(sw.toString());
                writer.newLine();
            }
            
            writer.flush();
            
        } catch (IOException e) {
            System.err.println("Failed to write to log file: " + e.getMessage());
        }
    }
    
    private boolean shouldLog(LogLevel level) {
        LogLevel currentLevel;
        try {
            currentLevel = LogLevel.valueOf(logLevel);
        } catch (IllegalArgumentException e) {
            currentLevel = LogLevel.INFO;
        }
        
        return level.ordinal() >= currentLevel.ordinal();
    }
    
    public void logSystemStart() {
        logInfo("=== Library Management System Started ===");
        logInfo("Log level: " + logLevel);
        logInfo("Log directory: " + logDirectory.toAbsolutePath());
    }
    
    public void logSystemShutdown() {
        logInfo("=== Library Management System Shutdown ===");
    }
    
    public void logUserActivity(String userId, String activity) {
        logInfo(String.format("User [%s]: %s", userId, activity));
    }
    

    public void logDatabaseOperation(String operation, String entityType, String entityId) {
        String message = String.format("Database %s: %s", operation, entityType);
        if (entityId != null) {
            message += " [ID: " + entityId + "]";
        }
        logInfo(message);
    }
    

    public void logBookBorrowed(String userId, String bookId, String loanId) {
        logInfo(String.format("Book borrowed - User: %s, Book: %s, Loan: %s", 
                             userId, bookId, loanId));
    }
    

    public void logBookReturned(String userId, String bookId, String loanId, boolean isOverdue) {
        String status = isOverdue ? " (OVERDUE)" : "";
        logInfo(String.format("Book returned - User: %s, Book: %s, Loan: %s%s", 
                             userId, bookId, loanId, status));
    }
    

    public void logConfigChange(String configKey, String oldValue, String newValue) {
        logInfo(String.format("Configuration changed - %s: '%s' -> '%s'", 
                             configKey, oldValue, newValue));
    }
    

    public void logFileOperation(String operation, String fileName, boolean success) {
        String status = success ? "SUCCESS" : "FAILED";
        logInfo(String.format("File %s: %s [%s]", operation, fileName, status));
    }
    
    public Path getCurrentLogFile() {
        String logFileName = String.format(LOG_FILE_PATTERN, LocalDateTime.now().format(DATE_FORMAT));
        return logDirectory.resolve(logFileName);
    }
    
    public void cleanupOldLogs() {
        try {
            LocalDateTime cutoffDate = LocalDateTime.now().minusDays(30);
            
            Files.list(logDirectory)
                .filter(path -> path.toString().endsWith(".log"))
                .filter(path -> {
                    try {
                        return Files.getLastModifiedTime(path)
                                   .toInstant()
                                   .isBefore(cutoffDate.atZone(java.time.ZoneId.systemDefault()).toInstant());
                    } catch (IOException e) {
                        return false;
                    }
                })
                .forEach(path -> {
                    try {
                        Files.delete(path);
                        logInfo("Deleted old log file: " + path.getFileName());
                    } catch (IOException e) {
                        logError("Failed to delete old log file: " + path.getFileName(), e);
                    }
                });
                
        } catch (IOException e) {
            logError("Failed to cleanup old log files", e);
        }
    }
}