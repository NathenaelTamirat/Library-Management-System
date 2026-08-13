package com.library.presentation;

import com.library.domain.Hold;
import com.library.domain.Member;
import com.library.service.HoldService;
import java.util.List;
import javafx.beans.property.SimpleStringProperty;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.TableColumn;
import javafx.scene.control.TableView;
import javafx.stage.Stage;

public final class HoldsController {
    private final HoldService holds;
    private final Member member;
    private final String selectedIsbn;

    @FXML
    private TableView<Hold> holdsTable;
    @FXML
    private TableColumn<Hold, String> isbnColumn;
    @FXML
    private TableColumn<Hold, String> placedColumn;
    @FXML
    private TableColumn<Hold, String> statusColumn;
    @FXML
    private TableColumn<Hold, String> expiresColumn;
    @FXML
    private Button refreshButton;
    @FXML
    private Button placeButton;
    @FXML
    private Label selectedBookLabel;
    @FXML
    private Label statusLabel;

    public HoldsController(HoldService holds, Member member, String selectedIsbn) {
        this.holds = holds;
        this.member = member;
        this.selectedIsbn = selectedIsbn;
    }

    @FXML
    private void initialize() {
        isbnColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().isbn()));
        placedColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().placedAt().toString()));
        statusColumn.setCellValueFactory(data ->
                new SimpleStringProperty(data.getValue().status().name()));
        expiresColumn.setCellValueFactory(data -> new SimpleStringProperty(
                data.getValue().expiresAt() == null
                        ? "—"
                        : data.getValue().expiresAt().toString()));
        isbnColumn.setSortable(true);
        placedColumn.setSortable(true);
        statusColumn.setSortable(true);
        expiresColumn.setSortable(true);
        holdsTable.setColumnResizePolicy(TableView.CONSTRAINED_RESIZE_POLICY_FLEX_LAST_COLUMN);
        selectedBookLabel.setText("Selected ISBN: " + selectedIsbn);
        refresh();
    }

    @FXML
    private void refresh() {
        Task<List<Hold>> task = new Task<>() {
            @Override
            protected List<Hold> call() throws Exception {
                return holds.holdsFor(member, member.id());
            }
        };
        refreshButton.disableProperty().bind(task.runningProperty());
        task.setOnSucceeded(ignored -> {
            holdsTable.setItems(FXCollections.observableArrayList(task.getValue()));
            statusLabel.setText(task.getValue().size() + " active hold(s)");
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Unable to load holds: " + task.getException().getMessage()));
        start(task, "holds-refresh");
    }

    @FXML
    private void placeSelected() {
        Task<Hold> task = new Task<>() {
            @Override
            protected Hold call() throws Exception {
                return holds.place(member, selectedIsbn);
            }
        };
        placeButton.disableProperty().bind(task.runningProperty());
        statusLabel.setText("Placing hold for " + selectedIsbn + "…");
        task.setOnSucceeded(ignored -> {
            statusLabel.setText("Hold placed for " + selectedIsbn);
            refresh();
        });
        task.setOnFailed(ignored ->
                statusLabel.setText("Unable to place hold: " + task.getException().getMessage()));
        start(task, "holds-place");
    }

    @FXML
    private void close() {
        ((Stage) statusLabel.getScene().getWindow()).close();
    }

    private static void start(Task<?> task, String threadName) {
        Thread worker = new Thread(task, threadName);
        worker.setDaemon(true);
        worker.start();
    }
}
