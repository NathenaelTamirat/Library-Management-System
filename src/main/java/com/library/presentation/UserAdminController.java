package com.library.presentation;

import com.library.data.UserAdminRepository.UserRecord;
import com.library.domain.Role;
import com.library.domain.User;
import com.library.service.UserAdminService;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.ChoiceBox;
import javafx.scene.control.Label;
import javafx.scene.control.PasswordField;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.scene.control.TextField;
import javafx.scene.control.TextInputDialog;
import javafx.stage.Stage;
import java.util.List;
import java.util.Optional;

public final class UserAdminController {
    private final UserAdminService users;
    private final User actor;

    @FXML
    private TableView<UserRecord> usersTable;
    @FXML
    private TableColumn<UserRecord, String> emailColumn;
    @FXML
    private TableColumn<UserRecord, String> nameColumn;
    @FXML
    private TableColumn<UserRecord, String> roleColumn;
    @FXML
    private TableColumn<UserRecord, String> activeColumn;
    @FXML
    private Button refreshButton;
    @FXML
    private Button activateButton;
    @FXML
    private Button deactivateButton;
    @FXML
    private ChoiceBox<Role> roleChoice;
    @FXML
    private Button roleButton;
    @FXML
    private Button resetButton;
    @FXML
    private TextField nameField;
    @FXML
    private TextField emailField;
    @FXML
    private PasswordField passwordField;
    @FXML
    private ChoiceBox<Role> createRoleChoice;
    @FXML
    private Button createButton;
    @FXML
    private Label statusLabel;

    public UserAdminController(UserAdminService users, User actor) {
        this.users = users;
        this.actor = actor;
    }

    @FXML
    private void initialize() {
        emailColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().email()));
        nameColumn.setCellValueFactory(data -> new SimpleStringProperty(data.getValue().name()));
        roleColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().role().name()));
        activeColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().active() ? "yes" : "no"));
        roleChoice.setItems(FXCollections.observableArrayList(Role.values()));
        roleChoice.getSelectionModel().select(Role.MEMBER);
        createRoleChoice.setItems(FXCollections.observableArrayList(Role.values()));
        createRoleChoice.getSelectionModel().select(Role.MEMBER);
        refresh();
    }

    @FXML
    private void refresh() {
        Task<List<UserRecord>> task = new Task<>() {
            @Override
            protected List<UserRecord> call() throws Exception {
                return users.list(actor);
            }
        };
        refreshButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            usersTable.setItems(FXCollections.observableArrayList(task.getValue()));
            statusLabel.setText(task.getValue().size() + " user(s)");
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Unable to load users: " + task.getException().getMessage()));
        Thread worker = new Thread(task, "user-admin-refresh");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void activateSelected() {
        UserRecord selected = selected();
        if (selected == null) {
            statusLabel.setText("Select a user");
            return;
        }
        runMutation(() -> users.activate(actor, selected.id()), "Activated " + selected.email());
    }

    @FXML
    private void deactivateSelected() {
        UserRecord selected = selected();
        if (selected == null) {
            statusLabel.setText("Select a user");
            return;
        }
        runMutation(() -> users.deactivate(actor, selected.id()), "Deactivated " + selected.email());
    }

    @FXML
    private void changeRoleSelected() {
        UserRecord selected = selected();
        Role role = roleChoice.getValue();
        if (selected == null || role == null) {
            statusLabel.setText("Select a user and role");
            return;
        }
        runMutation(
                () -> users.changeRole(actor, selected.id(), role),
                "Changed role for " + selected.email() + " to " + role);
    }

    @FXML
    private void resetPasswordSelected() {
        UserRecord selected = selected();
        if (selected == null) {
            statusLabel.setText("Select a user");
            return;
        }
        TextInputDialog dialog = new TextInputDialog();
        dialog.setTitle("Reset password");
        dialog.setHeaderText("Temporary password for " + selected.email());
        dialog.setContentText("Password");
        Optional<String> password = dialog.showAndWait();
        if (password.isEmpty() || password.orElseThrow().isBlank()) {
            statusLabel.setText("Password reset cancelled");
            return;
        }
        char[] chars = password.orElseThrow().toCharArray();
        runMutation(
                () -> users.resetPassword(actor, selected.id(), chars),
                "Password reset for " + selected.email());
    }

    @FXML
    private void createUser() {
        Role role = createRoleChoice.getValue();
        char[] password = passwordField.getText() == null
                ? new char[0]
                : passwordField.getText().toCharArray();
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                users.create(actor, nameField.getText(), emailField.getText(), password, role);
                return null;
            }
        };
        createButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            nameField.clear();
            emailField.clear();
            passwordField.clear();
            statusLabel.setText("User created");
            refresh();
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Create failed: " + task.getException().getMessage()));
        Thread worker = new Thread(task, "user-admin-create");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void close() {
        ((Stage) statusLabel.getScene().getWindow()).close();
    }

    private UserRecord selected() {
        return usersTable.getSelectionModel().getSelectedItem();
    }

    private void runMutation(Mutation mutation, String successMessage) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                mutation.run();
                return null;
            }
        };
        createButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            statusLabel.setText(successMessage);
            refresh();
        });
        task.setOnFailed(ignored -> {
            Throwable failure = task.getException();
            statusLabel.setText("Operation failed: " + failure.getMessage());
        });
        Thread worker = new Thread(task, "user-admin-mutation");
        worker.setDaemon(true);
        worker.start();
    }

    @FunctionalInterface
    private interface Mutation {
        void run() throws Exception;
    }
}
