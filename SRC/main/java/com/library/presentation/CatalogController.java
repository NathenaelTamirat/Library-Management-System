package com.library.presentation;

import com.library.domain.Book;
import com.library.service.CatalogService;
import java.util.List;
import javafx.collections.FXCollections;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.ListCell;
import javafx.scene.control.ListView;
import javafx.scene.control.TextField;

public final class CatalogController {
    private final CatalogService catalog;

    @FXML
    private TextField searchField;
    @FXML
    private Button searchButton;
    @FXML
    private ListView<Book> resultsList;
    @FXML
    private Label statusLabel;

    public CatalogController(CatalogService catalog) {
        this.catalog = catalog;
    }

    @FXML
    private void initialize() {
        resultsList.setCellFactory(ignored -> new ListCell<>() {
            @Override
            protected void updateItem(Book book, boolean empty) {
                super.updateItem(book, empty);
                setText(empty || book == null
                        ? null
                        : "%s — %s (%d available)".formatted(
                                book.title(), book.author(), book.availableCopies()));
            }
        });
        searchField.setOnAction(ignored -> search());
    }

    @FXML
    private void search() {
        String query = searchField.getText();
        Task<List<Book>> searchTask = createSearchTask(query);
        searchButton.disableProperty().bind(searchTask.runningProperty());
        statusLabel.textProperty().bind(searchTask.messageProperty());
        searchTask.setOnSucceeded(ignored -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText(searchTask.getValue().size() + " book(s) found");
            resultsList.setItems(FXCollections.observableArrayList(searchTask.getValue()));
        });
        searchTask.setOnFailed(ignored -> {
            statusLabel.textProperty().unbind();
            statusLabel.setText("Search failed: " + searchTask.getException().getMessage());
        });

        Thread worker = new Thread(searchTask, "catalog-search");
        worker.setDaemon(true);
        worker.start();
    }

    Task<List<Book>> createSearchTask(String query) {
        return new Task<>() {
            @Override
            protected List<Book> call() throws Exception {
                updateMessage("Searching…");
                return catalog.search(query);
            }
        };
    }
}
