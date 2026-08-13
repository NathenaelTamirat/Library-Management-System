package com.library.presentation;

import com.library.domain.User;
import com.library.security.AuthenticationService;
import java.util.Arrays;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TextField;

public final class LoginController {
    private final AuthenticationService authentication;
    private final Consumer<User> onAuthenticated;

    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private Button loginButton;
    @FXML
    private Label errorLabel;

    public LoginController(AuthenticationService authentication, Consumer<User> onAuthenticated) {
        this.authentication = authentication;
        this.onAuthenticated = onAuthenticated;
    }

    @FXML
    private void initialize() {
        passwordField.setOnAction(ignored -> login());
    }

    @FXML
    private void login() {
        String email = emailField.getText();
        char[] password = passwordField.getText().toCharArray();
        passwordField.clear();
        Task<Optional<User>> loginTask = new Task<>() {
            @Override
            protected Optional<User> call() {
                try {
                    return authentication.authenticate(email, password);
                } finally {
                    Arrays.fill(password, '\0');
                }
            }
        };
        loginButton.disableProperty().bind(loginTask.runningProperty());
        errorLabel.setText("Signing in…");
        loginTask.setOnSucceeded(ignored -> {
            Optional<User> user = loginTask.getValue();
            if (user.isPresent()) {
                onAuthenticated.accept(user.orElseThrow());
            } else {
                errorLabel.setText("Invalid email or password");
            }
        });
        loginTask.setOnFailed(ignored ->
                errorLabel.setText("Sign-in service is unavailable"));

        Thread worker = new Thread(loginTask, "authentication");
        worker.setDaemon(true);
        worker.start();
    }
}
