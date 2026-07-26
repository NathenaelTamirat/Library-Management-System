package com.library.presentation;

import com.library.domain.AuditEntry;
import com.library.domain.User;
import com.library.service.AuditService;
import java.util.List;
import java.util.UUID;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public final class AuditLogController {
    private static final int LIMIT = 100;

    private final AuditService audits;
    private final User actor;

    @FXML
    private TextField actionFilter;
    @FXML
    private TextField userFilter;
    @FXML
    private ListView<AuditEntry> entriesList;
    @FXML
    private Button refreshButton;
    @FXML
    private Label statusLabel;

    public AuditLogController(AuditService audits, User actor) {
        this.audits = audits;
        this.actor = actor;
    }

    @FXML
    private void initialize() {
        entriesList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(AuditEntry entry, boolean empty) {
                super.updateItem(entry, empty);
                setText(empty || entry == null
                        ? null
                        : "%s | %s | user=%s | %s".formatted(
                                entry.occurredAt(),
                                entry.action(),
                                entry.userId().map(Object::toString).orElse("-"),
                                entry.details()));
            }
        });
        actionFilter.setOnAction(ignored -> refresh());
        userFilter.setOnAction(ignored -> refresh());
        refresh();
    }

    @FXML
    private void refresh() {
        String action = actionFilter.getText() == null ? "" : actionFilter.getText().strip();
        String userText = userFilter.getText() == null ? "" : userFilter.getText().strip();
        Task<List<AuditEntry>> task = new Task<>() {
            @Override
            protected List<AuditEntry> call() throws Exception {
                if (!userText.isBlank()) {
                    UUID userId = UUID.fromString(userText);
                    List<AuditEntry> entries = audits.byUser(actor, userId, LIMIT);
                    if (action.isBlank()) {
                        return entries;
                    }
                    return entries.stream()
                            .filter(entry -> entry.action().equalsIgnoreCase(action))
                            .toList();
                }
                if (action.isBlank()) {
                    return audits.recent(actor, LIMIT);
                }
                return audits.byAction(actor, action, LIMIT);
            }
        };
        refreshButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            entriesList.setItems(FXCollections.observableArrayList(task.getValue()));
            statusLabel.setText(task.getValue().size() + " audit entr" +
                    (task.getValue().size() == 1 ? "y" : "ies"));
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Unable to load audit log: "
                        + task.getException().getMessage()));
        Thread worker = new Thread(task, "audit-refresh");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void close() {
        ((Stage) statusLabel.getScene().getWindow()).close();
    }
}
