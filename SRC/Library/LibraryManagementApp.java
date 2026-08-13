package library;

import library.gui.LibraryManagementGUI;
import javax.swing.*;

public class LibraryManagementApp {
    
    public static void main(String[] args) {
        SwingUtilities.invokeLater(() -> {
            try {
                new LibraryManagementGUI().setVisible(true);
            } catch (Exception e) {
                e.printStackTrace();
                JOptionPane.showMessageDialog(null, 
                        "Failed to start application: " + e.getMessage(), 
                        "Error", 
                        JOptionPane.ERROR_MESSAGE);
            }
        });
    }
}