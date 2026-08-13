package com.library.presentation;

import com.library.domain.LoanPolicy;
import com.library.domain.User;
import com.library.service.LoanPolicyService;
import java.math.BigDecimal;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public final class PolicySettingsController {
    private final LoanPolicyService policies;
    private final User actor;

    @FXML
    private Spinner<Integer> loanDaysSpinner;
    @FXML
    private TextField dailyFineField;
    @FXML
    private TextField replacementFineField;
    @FXML
    private Spinner<Integer> maxRenewalsSpinner;
    @FXML
    private Spinner<Integer> borrowLimitSpinner;
    @FXML
    private Button saveButton;
    @FXML
    private Label statusLabel;

    public PolicySettingsController(LoanPolicyService policies, User actor) {
        this.policies = policies;
        this.actor = actor;
    }

    @FXML
    private void initialize() {
        loanDaysSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 365, 14));
        maxRenewalsSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 20, 2));
        borrowLimitSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 50, 5));
        Task<LoanPolicy> task = new Task<>() {
            @Override
            protected LoanPolicy call() throws Exception {
                return policies.current();
            }
        };
        task.setOnSucceeded(ignored -> apply(task.getValue()));
        task.setOnFailed(ignored ->
                statusLabel.setText("Unable to load policy: " + task.getException().getMessage()));
        Thread worker = new Thread(task, "policy-load");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void save() {
        LoanPolicy policy;
        try {
            policy = new LoanPolicy(
                    loanDaysSpinner.getValue(),
                    new BigDecimal(dailyFineField.getText().strip()),
                    new BigDecimal(replacementFineField.getText().strip()),
                    maxRenewalsSpinner.getValue(),
                    borrowLimitSpinner.getValue());
        } catch (RuntimeException failure) {
            statusLabel.setText("Invalid policy values: " + failure.getMessage());
            return;
        }
        Task<LoanPolicy> task = new Task<>() {
            @Override
            protected LoanPolicy call() throws Exception {
                return policies.update(actor, policy);
            }
        };
        saveButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            apply(task.getValue());
            statusLabel.setText("Policy saved (restart may be needed for active sessions)");
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Save failed: " + task.getException().getMessage()));
        Thread worker = new Thread(task, "policy-save");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void close() {
        ((Stage) statusLabel.getScene().getWindow()).close();
    }

    private void apply(LoanPolicy policy) {
        loanDaysSpinner.getValueFactory().setValue(policy.loanDays());
        dailyFineField.setText(policy.dailyFine().toPlainString());
        replacementFineField.setText(policy.replacementFine().toPlainString());
        maxRenewalsSpinner.getValueFactory().setValue(policy.maxRenewals());
        borrowLimitSpinner.getValueFactory().setValue(policy.borrowLimit());
    }
}
