package library.util;

import java.io.*;
import java.nio.file.*;
import java.util.Properties;

/**
 * Configuration manager for handling application configuration.
 * This class demonstrates file I/O operations using Properties and BufferedReader/Writer.
 */
public class ConfigManager {
    private static final String DEFAULT_CONFIG_FILE = "config/config.properties";
    private static final String RESOURCE_CONFIG_FILE = "config.properties";
    
    private static ConfigManager instance;
    private Properties properties;
    private Path configFile;
    private Logger logger;
    
    /**
     * Private constructor for singleton pattern.
     */
    private ConfigManager() {
        this.properties = new Properties();
        this.configFile = Paths.get(DEFAULT_CONFIG_FILE);
        this.logger = Logger.getInstance();
        loadConfiguration();
    }
    
    /**
     * Gets the singleton instance of ConfigManager.
     * 
     * @return the ConfigManager instance
     */
    public static synchronized ConfigManager getInstance() {
        if (instance == null) {
            instance = new ConfigManager();
        }
        return instance;
    }
    
    /**
     * Loads configuration from file.
     * First tries to load from external config file, then falls back to resource file.
     */
    private void loadConfiguration() {
        // Try to load from external config file first
        if (Files.exists(configFile)) {
            loadFromFile(configFile);
        } else {
            // Fall back to resource file
            loadFromResource();
            // Create external config file for future use
            createDefaultConfigFile();
        }
        
        logger.logInfo("Configuration loaded successfully");
    }
    
    /**
     * Loads configuration from an external file using BufferedReader.
     * 
     * @param file the path to the configuration file
     */
    private void loadFromFile(Path file) {
        try (BufferedReader reader = Files.newBufferedReader(file)) {
            properties.load(reader);
            logger.logFileOperation("READ", file.toString(), true);
        } catch (IOException e) {
            logger.logError("Failed to load configuration from file: " + file, e);
            logger.logFileOperation("READ", file.toString(), false);
        }
    }
    
    /**
     * Loads configuration from resource file.
     */
    private void loadFromResource() {
        try (InputStream inputStream = getClass().getClassLoader().getResourceAsStream(RESOURCE_CONFIG_FILE)) {
            if (inputStream != null) {
                properties.load(inputStream);
                logger.logInfo("Configuration loaded from resource file");
            } else {
                logger.logWarning("Resource configuration file not found, using defaults");
                loadDefaultProperties();
            }
        } catch (IOException e) {
            logger.logError("Failed to load configuration from resource", e);
            loadDefaultProperties();
        }
    }
    
    /**
     * Creates a default configuration file in the config directory.
     */
    private void createDefaultConfigFile() {
        try {
            // Create config directory if it doesn't exist
            Path configDir = configFile.getParent();
            if (configDir != null && !Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            
            // Save current properties to external file
            saveConfiguration();
            logger.logInfo("Default configuration file created: " + configFile);
        } catch (IOException e) {
            logger.logError("Failed to create default configuration file", e);
        }
    }
    
    /**
     * Loads default properties when no configuration file is available.
     */
    private void loadDefaultProperties() {
        properties.setProperty("database.url", "jdbc:sqlite:data/library.db");
        properties.setProperty("database.driver", "org.sqlite.JDBC");
        properties.setProperty("system.name", "Library Management System");
        properties.setProperty("system.version", "1.0.0");
        properties.setProperty("max.loan.days", "14");
        properties.setProperty("max.books.per.user", "5");
        properties.setProperty("fine.per.day", "0.50");
        properties.setProperty("log.level", "INFO");
        properties.setProperty("log.directory", "logs");
        properties.setProperty("backup.directory", "backups");
        properties.setProperty("backup.auto.enabled", "true");
        properties.setProperty("backup.retention.days", "30");
        
        logger.logInfo("Default configuration properties loaded");
    }
    
    /**
     * Saves the current configuration to file using BufferedWriter.
     */
    public void saveConfiguration() {
        try {
            // Ensure config directory exists
            Path configDir = configFile.getParent();
            if (configDir != null && !Files.exists(configDir)) {
                Files.createDirectories(configDir);
            }
            
            try (BufferedWriter writer = Files.newBufferedWriter(configFile)) {
                properties.store(writer, "Library Management System Configuration");
                logger.logFileOperation("WRITE", configFile.toString(), true);
                logger.logInfo("Configuration saved successfully");
            }
        } catch (IOException e) {
            logger.logError("Failed to save configuration", e);
            logger.logFileOperation("WRITE", configFile.toString(), false);
        }
    }
    
    /**
     * Gets a configuration property value.
     * 
     * @param key the property key
     * @return the property value, or null if not found
     */
    public String getProperty(String key) {
        return properties.getProperty(key);
    }
    
    /**
     * Gets a configuration property value with a default.
     * 
     * @param key the property key
     * @param defaultValue the default value if key is not found
     * @return the property value, or default value if not found
     */
    public String getProperty(String key, String defaultValue) {
        return properties.getProperty(key, defaultValue);
    }
    
    /**
     * Sets a configuration property value.
     * 
     * @param key the property key
     * @param value the property value
     */
    public void setProperty(String key, String value) {
        String oldValue = properties.getProperty(key);
        properties.setProperty(key, value);
        logger.logConfigChange(key, oldValue, value);
    }
    
    /**
     * Gets a property as an integer.
     * 
     * @param key the property key
     * @param defaultValue the default value if key is not found or invalid
     * @return the property value as integer
     */
    public int getIntProperty(String key, int defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Integer.parseInt(value.trim());
            } catch (NumberFormatException e) {
                logger.logWarning("Invalid integer value for property " + key + ": " + value);
            }
        }
        return defaultValue;
    }
    
    /**
     * Gets a property as a double.
     * 
     * @param key the property key
     * @param defaultValue the default value if key is not found or invalid
     * @return the property value as double
     */
    public double getDoubleProperty(String key, double defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            try {
                return Double.parseDouble(value.trim());
            } catch (NumberFormatException e) {
                logger.logWarning("Invalid double value for property " + key + ": " + value);
            }
        }
        return defaultValue;
    }
    
    /**
     * Gets a property as a boolean.
     * 
     * @param key the property key
     * @param defaultValue the default value if key is not found
     * @return the property value as boolean
     */
    public boolean getBooleanProperty(String key, boolean defaultValue) {
        String value = properties.getProperty(key);
        if (value != null) {
            return Boolean.parseBoolean(value.trim());
        }
        return defaultValue;
    }
    
    /**
     * Sets an integer property.
     * 
     * @param key the property key
     * @param value the integer value
     */
    public void setIntProperty(String key, int value) {
        setProperty(key, String.valueOf(value));
    }
    
    /**
     * Sets a double property.
     * 
     * @param key the property key
     * @param value the double value
     */
    public void setDoubleProperty(String key, double value) {
        setProperty(key, String.valueOf(value));
    }
    
    /**
     * Sets a boolean property.
     * 
     * @param key the property key
     * @param value the boolean value
     */
    public void setBooleanProperty(String key, boolean value) {
        setProperty(key, String.valueOf(value));
    }
    
    /**
     * Checks if a property exists.
     * 
     * @param key the property key
     * @return true if the property exists, false otherwise
     */
    public boolean hasProperty(String key) {
        return properties.containsKey(key);
    }
    
    /**
     * Removes a property.
     * 
     * @param key the property key to remove
     * @return the previous value, or null if not found
     */
    public String removeProperty(String key) {
        String oldValue = properties.getProperty(key);
        properties.remove(key);
        if (oldValue != null) {
            logger.logConfigChange(key, oldValue, "<removed>");
        }
        return oldValue;
    }
    
    /**
     * Gets the database URL.
     * 
     * @return the database URL
     */
    public String getDatabaseUrl() {
        return getProperty("database.url", "jdbc:sqlite:data/library.db");
    }
    
    /**
     * Gets the database driver class name.
     * 
     * @return the database driver class name
     */
    public String getDatabaseDriver() {
        return getProperty("database.driver", "org.sqlite.JDBC");
    }
    
    /**
     * Gets the maximum loan days.
     * 
     * @return the maximum loan days
     */
    public int getMaxLoanDays() {
        return getIntProperty("max.loan.days", 14);
    }
    
    /**
     * Gets the maximum books per user.
     * 
     * @return the maximum books per user
     */
    public int getMaxBooksPerUser() {
        return getIntProperty("max.books.per.user", 5);
    }
    
    /**
     * Gets the fine per day amount.
     * 
     * @return the fine per day amount
     */
    public double getFinePerDay() {
        return getDoubleProperty("fine.per.day", 0.50);
    }
    
    /**
     * Gets the log level.
     * 
     * @return the log level
     */
    public String getLogLevel() {
        return getProperty("log.level", "INFO");
    }
    
    /**
     * Gets the log directory.
     * 
     * @return the log directory
     */
    public String getLogDirectory() {
        return getProperty("log.directory", "logs");
    }
    
    /**
     * Gets the backup directory.
     * 
     * @return the backup directory
     */
    public String getBackupDirectory() {
        return getProperty("backup.directory", "backups");
    }
    
    /**
     * Checks if auto backup is enabled.
     * 
     * @return true if auto backup is enabled, false otherwise
     */
    public boolean isAutoBackupEnabled() {
        return getBooleanProperty("backup.auto.enabled", true);
    }
    
    /**
     * Gets the backup retention days.
     * 
     * @return the backup retention days
     */
    public int getBackupRetentionDays() {
        return getIntProperty("backup.retention.days", 30);
    }
    
    /**
     * Gets the system name.
     * 
     * @return the system name
     */
    public String getSystemName() {
        return getProperty("system.name", "Library Management System");
    }
    
    /**
     * Gets the system version.
     * 
     * @return the system version
     */
    public String getSystemVersion() {
        return getProperty("system.version", "1.0.0");
    }
    
    /**
     * Reloads the configuration from file.
     */
    public void reloadConfiguration() {
        properties.clear();
        loadConfiguration();
        logger.logInfo("Configuration reloaded");
    }
    
    /**
     * Gets all properties as a copy.
     * 
     * @return a copy of all properties
     */
    public Properties getAllProperties() {
        Properties copy = new Properties();
        copy.putAll(properties);
        return copy;
    }
    
    /**
     * Gets the configuration file path.
     * 
     * @return the configuration file path
     */
    public Path getConfigFilePath() {
        return configFile;
    }
}