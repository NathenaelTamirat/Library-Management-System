package com.library.presentation;

import com.library.domain.CirculationSummary;
import com.library.domain.User;
import com.library.service.CirculationReportService;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public final class CirculationSummaryController {
    private final CirculationReportService reports;
    private final User actor;

    @FXML
    private Label openLoansLabel;
    @FXML
    private Label overdueLoansLabel;
    @FXML
    private Label unpaidFinesLabel;
    @FXML
    private Label availableCopiesLabel;
    @FXML
    private Button refreshButton;
    @FXML
    private Label statusLabel;

    public CirculationSummaryController(CirculationReportService reports, User actor) {
        this.reports = reports;
        this.actor = actor;
    }

    @FXML
    private void initialize() {
        refresh();
    }

    @FXML
    private void refresh() {
        Task<CirculationSummary> task = new Task<>() {
            @Override
            protected CirculationSummary call() throws Exception {
                return reports.summarize(actor);
            }
        };
        refreshButton.disableProperty().bind(task.runningProperty());
        statusLabel.setText("Loading circulation summary…");
        task.setOnSucceeded(ignored -> {
            apply(task.getValue());
            statusLabel.setText("Circulation summary updated");
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Unable to load summary: " + task.getException().getMessage()));
        Thread worker = new Thread(task, "circulation-summary-load");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void close() {
        ((Stage) statusLabel.getScene().getWindow()).close();
    }

    private void apply(CirculationSummary summary) {
        openLoansLabel.setText(Long.toString(summary.openLoans()));
        overdueLoansLabel.setText(Long.toString(summary.overdueLoans()));
        unpaidFinesLabel.setText(
                summary.unpaidFines() + " (" + summary.unpaidFineTotal().toPlainString() + ")");
        availableCopiesLabel.setText(
                summary.availableCopies() + " / " + summary.totalCopies());
    }
}
