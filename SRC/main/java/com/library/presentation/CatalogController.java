package com.library.presentation;

import com.library.domain.Book;
import com.library.domain.Member;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import com.library.service.CatalogService;
import com.library.service.CirculationService;
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
    private final CirculationService circulation;
    private final User currentUser;
    private final AuthorizationService authorization;

    @FXML
    private TextField searchField;
    @FXML
    private Button searchButton;
    @FXML
    private Button borrowButton;
    @FXML
    private Button deleteButton;
    @FXML
    private ListView<Book> resultsList;
    @FXML
    private Label statusLabel;

    public CatalogController(
            CatalogService catalog,
            CirculationService circulation,
            User currentUser,
            AuthorizationService authorization) {
        this.catalog = catalog;
        this.circulation = circulation;
        this.currentUser = currentUser;
        this.authorization = authorization;
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
        boolean member = currentUser instanceof Member
                && authorization.isAllowed(currentUser.role(), Permission.BORROW_BOOK);
        borrowButton.setVisible(member);
        borrowButton.setManaged(member);
        boolean catalogManager = authorization.isAllowed(
                currentUser.role(), Permission.MANAGE_CATALOG);
        deleteButton.setVisible(catalogManager);
        deleteButton.setManaged(catalogManager);
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

    @FXML
    private void borrowSelected() {
        Book selected = resultsList.getSelectionModel().getSelectedItem();
        if (selected == null || !(currentUser instanceof Member member)) {
            statusLabel.setText("Select a book to borrow");
            return;
        }
        runMutation(borrowButton, "Checking out…", () ->
                circulation.checkout(member, selected.isbn()));
    }

    @FXML
    private void deleteSelected() {
        Book selected = resultsList.getSelectionModel().getSelectedItem();
        if (selected == null) {
            statusLabel.setText("Select a book to delete");
            return;
        }
        runMutation(deleteButton, "Deleting…", () ->
                catalog.delete(currentUser, selected.isbn()));
    }

    private void runMutation(Button source, String message, CheckedOperation operation) {
        Task<Void> task = new Task<>() {
            @Override
            protected Void call() throws Exception {
                operation.run();
                return null;
            }
        };
        source.disableProperty().bind(task.runningProperty());
        statusLabel.setText(message);
        task.setOnSucceeded(ignored -> search());
        task.setOnFailed(ignored ->
                statusLabel.setText("Operation failed: " + task.getException().getMessage()));
        Thread worker = new Thread(task, "catalog-mutation");
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

    @FunctionalInterface
    private interface CheckedOperation {
        void run() throws Exception;
    }
}
