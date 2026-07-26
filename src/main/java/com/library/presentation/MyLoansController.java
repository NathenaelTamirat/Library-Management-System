package com.library.presentation;

import com.library.domain.Loan;
import com.library.domain.ReturnReceipt;
import com.library.domain.User;
import com.library.service.CirculationService;
import java.util.List;
import java.util.UUID;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

public final class MyLoansController {
    private final CirculationService circulation;
    private final User actor;
    private final UUID memberId;

    @FXML
    private ListView<Loan> loansList;
    @FXML
    private Button refreshButton;
    @FXML
    private Button returnButton;
    @FXML
    private Button renewButton;
    @FXML
    private Label statusLabel;

    public MyLoansController(CirculationService circulation, User actor, UUID memberId) {
        this.circulation = circulation;
        this.actor = actor;
        this.memberId = memberId;
    }

    @FXML
    private void initialize() {
        loansList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Loan loan, boolean empty) {
                super.updateItem(loan, empty);
                setText(empty || loan == null
                        ? null
                        : "%s — due %s [%s]".formatted(
                                loan.isbn(), loan.dueDate(), loan.id()));
            }
        });
        refresh();
    }

    @FXML
    private void refresh() {
        Task<List<Loan>> task = new Task<>() {
            @Override
            protected List<Loan> call() throws Exception {
                return circulation.openLoansFor(actor, memberId);
            }
        };
        refreshButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            loansList.setItems(FXCollections.observableArrayList(task.getValue()));
            statusLabel.setText(task.getValue().size() + " open loan(s)");
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Unable to load loans: " + task.getException().getMessage()));
        start(task, "my-loans-refresh");
    }

    @FXML
    private void returnSelected() {
        Loan selected = loansList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a loan to return");
            return;
        }
        Task<ReturnReceipt> task = new Task<>() {
            @Override
            protected ReturnReceipt call() throws Exception {
                return circulation.returnLoan(actor, selected.id());
            }
        };
        returnButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            ReturnReceipt receipt = task.getValue();
            String fine = receipt.fine()
                    .map(value -> value.amount().toPlainString())
                    .orElse("0.00");
            Alert alert = new Alert(Alert.AlertType.INFORMATION);
            alert.setTitle("Return receipt");
            alert.setHeaderText("Return completed");
            alert.setContentText("Loan ID: " + receipt.loan().id() + "\nFine: " + fine);
            alert.showAndWait();
            refresh();
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Return failed: " + task.getException().getMessage()));
        start(task, "my-loans-return");
    }

    @FXML
    private void renewSelected() {
        Loan selected = loansList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a loan to renew");
            return;
        }
        Task<Loan> task = new Task<>() {
            @Override
            protected Loan call() throws Exception {
                return circulation.renew(actor, selected.id());
            }
        };
        renewButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            statusLabel.setText("Renewed until " + task.getValue().dueDate());
            refresh();
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Renew failed: " + task.getException().getMessage()));
        start(task, "my-loans-renew");
    }

    @FXML
    private void close() {
        ((Stage) statusLabel.getScene().getWindow()).close();
    }

    private static void start(Task<?> task, String name) {
        Thread worker = new Thread(task, name);
        worker.setDaemon(true);
        worker.start();
    }
}
