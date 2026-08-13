package com.library.presentation;

import com.library.domain.Loan;
import com.library.domain.User;
import com.library.service.CirculationService;
import java.util.List;
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

public final class OverdueLoansController {
    private final CirculationService circulation;
    private final User actor;

    @FXML
    private ListView<Loan> loansList;
    @FXML
    private Button refreshButton;
    @FXML
    private Button reconcileButton;
    @FXML
    private Button markLostButton;
    @FXML
    private Label statusLabel;

    public OverdueLoansController(CirculationService circulation, User actor) {
        this.circulation = circulation;
        this.actor = actor;
    }

    @FXML
    private void initialize() {
        loansList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Loan loan, boolean empty) {
                super.updateItem(loan, empty);
                setText(empty || loan == null
                        ? null
                        : "%s — member %s — due %s".formatted(
                                loan.isbn(), loan.userId(), loan.dueDate()));
            }
        });
        refresh();
    }

    @FXML
    private void refresh() {
        Task<List<Loan>> task = new Task<>() {
            @Override
            protected List<Loan> call() throws Exception {
                return circulation.overdueLoans(actor);
            }
        };
        refreshButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            loansList.setItems(FXCollections.observableArrayList(task.getValue()));
            statusLabel.setText(task.getValue().size() + " overdue loan(s)");
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Unable to load overdue loans: "
                        + task.getException().getMessage()));
        start(task, "overdue-refresh");
    }

    @FXML
    private void reconcile() {
        Task<Integer> task = new Task<>() {
            @Override
            protected Integer call() throws Exception {
                return circulation.reconcileOverdue(actor);
            }
        };
        reconcileButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            statusLabel.setText("Reconciled " + task.getValue() + " loan(s)");
            refresh();
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Reconcile failed: " + task.getException().getMessage()));
        start(task, "overdue-reconcile");
    }

    @FXML
    private void markLostSelected() {
        Loan selected = loansList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select an overdue loan");
            return;
        }
        Alert confirm = new Alert(Alert.AlertType.CONFIRMATION);
        confirm.setTitle("Mark loan lost");
        confirm.setHeaderText("Mark this loan as lost and charge a replacement fine?");
        confirm.setContentText(selected.isbn() + " — member " + selected.userId());
        if (confirm.showAndWait().orElse(ButtonType.CANCEL) != ButtonType.OK) {
            statusLabel.setText("Mark lost cancelled");
            return;
        }
        Task<Loan> task = new Task<>() {
            @Override
            protected Loan call() throws Exception {
                return circulation.markLost(actor, selected.id());
            }
        };
        markLostButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            statusLabel.setText("Marked lost: " + task.getValue().id());
            refresh();
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Mark lost failed: " + task.getException().getMessage()));
        start(task, "overdue-mark-lost");
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
