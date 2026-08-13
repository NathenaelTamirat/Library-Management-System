package com.library.presentation;

import com.library.domain.Fine;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import com.library.service.FineService;
import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.ButtonType;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

public final class FinesController {
    private final FineService fines;
    private final User actor;
    private final UUID memberId;
    private final AuthorizationService authorization;

    @FXML
    private Label balanceLabel;
    @FXML
    private ListView<Fine> finesList;
    @FXML
    private Button refreshButton;
    @FXML
    private Button payButton;
    @FXML
    private Label statusLabel;

    public FinesController(
            FineService fines,
            User actor,
            UUID memberId,
            AuthorizationService authorization) {
        this.fines = fines;
        this.actor = actor;
        this.memberId = memberId;
        this.authorization = authorization;
    }

    @FXML
    private void initialize() {
        boolean canPay = authorization.isAllowed(actor.role(), Permission.MANAGE_LOANS);
        payButton.setVisible(canPay);
        payButton.setManaged(canPay);
        finesList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Fine fine, boolean empty) {
                super.updateItem(fine, empty);
                setText(empty || fine == null
                        ? null
                        : "%s remaining (of %s) — loan %s (issued %s)".formatted(
                                fine.remaining().toPlainString(),
                                fine.amount().toPlainString(),
                                fine.loanId(),
                                fine.issuedDate()));
            }
        });
        refresh();
    }

    @FXML
    private void refresh() {
        Task<List<Fine>> task = new Task<>() {
            @Override
            protected List<Fine> call() throws Exception {
                return fines.unpaidFinesFor(actor, memberId);
            }
        };
        refreshButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            List<Fine> unpaid = task.getValue();
            finesList.setItems(FXCollections.observableArrayList(unpaid));
            BigDecimal balance = unpaid.stream()
                    .map(Fine::remaining)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
            balanceLabel.setText("Balance: " + balance.toPlainString());
            statusLabel.setText(unpaid.size() + " unpaid fine(s)");
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Unable to load fines: " + task.getException().getMessage()));
        Thread worker = new Thread(task, "fines-refresh");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void paySelected() {
        Fine selected = finesList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a fine to pay");
            return;
        }
        Alert confirmation = new Alert(Alert.AlertType.CONFIRMATION);
        confirmation.setTitle("Confirm fine payment");
        confirmation.setHeaderText("Pay selected fine?");
        confirmation.setContentText("Amount: " + selected.remaining().toPlainString());
        if (confirmation.showAndWait().filter(ButtonType.OK::equals).isEmpty()) {
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                fines.pay(actor, selected.id());
                return null;
            }
        };
        payButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> refresh());
        task.setOnFailed(ignored ->
                statusLabel.setText("Payment failed: " + task.getException().getMessage()));
        Thread worker = new Thread(task, "fines-pay");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void close() {
        ((Stage) statusLabel.getScene().getWindow()).close();
    }
}
