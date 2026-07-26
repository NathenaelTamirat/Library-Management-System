package com.library.presentation;

import com.library.domain.BookRecommendation;
import com.library.domain.Member;
import com.library.service.CirculationService;
import com.library.service.RecommendationService;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.stage.Stage;

public final class RecommendationController {
    private static final int DEFAULT_LIMIT = 10;

    private final RecommendationService recommendations;
    private final CirculationService circulation;
    private final Member member;

    @FXML
    private ListView<BookRecommendation> recommendationsList;
    @FXML
    private Button refreshButton;
    @FXML
    private Button borrowButton;
    @FXML
    private Label statusLabel;

    public RecommendationController(
            RecommendationService recommendations,
            CirculationService circulation,
            Member member) {
        this.recommendations = recommendations;
        this.circulation = circulation;
        this.member = member;
    }

    @FXML
    private void initialize() {
        recommendationsList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(BookRecommendation recommendation, boolean empty) {
                super.updateItem(recommendation, empty);
                setText(empty || recommendation == null
                        ? null
                        : "%s — %s%n%s · %d available".formatted(
                                recommendation.book().title(),
                                recommendation.book().author(),
                                recommendation.reason(),
                                recommendation.book().availableCopies()));
            }
        });
        refresh();
    }

    @FXML
    private void refresh() {
        Task<List<BookRecommendation>> task = new Task<>() {
            @Override
            protected List<BookRecommendation> call() throws Exception {
                updateMessage("Finding books for you…");
                return recommendations.recommendFor(member, member.id(), DEFAULT_LIMIT);
            }
        };
        refreshButton.disableProperty().bind(task.runningProperty());
        statusLabel.textProperty().bind(task.messageProperty());
        task.setOnSucceeded(ignored -> {
            statusLabel.textProperty().unbind();
            List<BookRecommendation> result = task.getValue();
            recommendationsList.setItems(FXCollections.observableArrayList(result));
            statusLabel.setText(result.isEmpty()
                    ? "No recommendations yet"
                    : result.size() + " recommendation(s)");
        });
        task.setOnFailed(ignored -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Recommendations unavailable: "
                    + task.getException().getMessage());
        });
        start(task, "recommendation-load");
    }

    @FXML
    private void borrowSelected() {
        BookRecommendation selected =
                recommendationsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a recommendation to borrow");
            return;
        }
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                circulation.checkout(member, selected.book().isbn());
                return null;
            }
        };
        borrowButton.disableProperty().bind(task.runningProperty());
        statusLabel.setText("Checking out " + selected.book().title() + "…");
        task.setOnSucceeded(ignored -> refresh());
        task.setOnFailed(ignored ->
                statusLabel.setText("Checkout failed: " + task.getException().getMessage()));
        start(task, "recommendation-checkout");
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
