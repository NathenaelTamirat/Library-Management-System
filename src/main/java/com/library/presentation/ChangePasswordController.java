package com.library.presentation;

import com.library.domain.User;
import com.library.security.AuthenticationService;
import java.util.Arrays;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.stage.Stage;

public final class ChangePasswordController {
    private final AuthenticationService authentication;
    private final User actor;

    @FXML
    private PasswordField currentPasswordField;
    @FXML
    private PasswordField newPasswordField;
    @FXML
    private PasswordField confirmPasswordField;
    @FXML
    private Button saveButton;
    @FXML
    private Label statusLabel;

    public ChangePasswordController(AuthenticationService authentication, User actor) {
        this.authentication = authentication;
        this.actor = actor;
    }

    @FXML
    private void save() {
        char[] current = currentPasswordField.getText() == null
                ? new char[0]
                : currentPasswordField.getText().toCharArray();
        char[] next = newPasswordField.getText() == null
                ? new char[0]
                : newPasswordField.getText().toCharArray();
        char[] confirm = confirmPasswordField.getText() == null
                ? new char[0]
                : confirmPasswordField.getText().toCharArray();
        if (!Arrays.equals(next, confirm)) {
            Arrays.fill(current, '\0');
            Arrays.fill(next, '\0');
            Arrays.fill(confirm, '\0');
            statusLabel.setText("New passwords do not match");
            return;
        }
        if (next.length < 8) {
            Arrays.fill(current, '\0');
            Arrays.fill(next, '\0');
            Arrays.fill(confirm, '\0');
            statusLabel.setText("New password must be at least 8 characters");
            return;
        }
        Arrays.fill(confirm, '\0');
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                authentication.changePassword(actor, current, next);
                return null;
            }
        };
        saveButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            currentPasswordField.clear();
            newPasswordField.clear();
            confirmPasswordField.clear();
            statusLabel.setText("Password updated");
            close();
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Unable to change password: "
                        + task.getException().getMessage()));
        Thread worker = new Thread(task, "change-password");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void close() {
        ((Stage) statusLabel.getScene().getWindow()).close();
    }
}
