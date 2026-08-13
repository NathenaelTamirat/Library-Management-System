package com.library.presentation;

import com.library.domain.Book;
import com.library.domain.User;
import com.library.service.CatalogService;
import java.util.Optional;
import java.util.function.Consumer;
import javafx.concurrent.Task;
import javafx.fxml.FXML;
import javafx.scene.control.Button;
import javafx.scene.control.Label;
import javafx.scene.control.Spinner;
import javafx.scene.control.SpinnerValueFactory;
import javafx.scene.control.TextField;
import javafx.stage.Stage;

public final class BookEditorController {
    private final CatalogService catalog;
    private final User actor;
    private final Optional<Book> existing;
    private final Consumer<Book> onSaved;

    @FXML
    private Label titleLabel;
    @FXML
    private TextField isbnField;
    @FXML
    private TextField titleField;
    @FXML
    private TextField authorField;
    @FXML
    private TextField genreField;
    @FXML
    private TextField publisherField;
    @FXML
    private TextField subjectField;
    @FXML
    private Spinner<Integer> yearSpinner;
    @FXML
    private Spinner<Integer> copiesSpinner;
    @FXML
    private Button saveButton;
    @FXML
    private Label errorLabel;

    public BookEditorController(
            CatalogService catalog,
            User actor,
            Optional<Book> existing,
            Consumer<Book> onSaved) {
        this.catalog = catalog;
        this.actor = actor;
        this.existing = existing;
        this.onSaved = onSaved;
    }

    @FXML
    private void initialize() {
        copiesSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(1, 500, 1));
        yearSpinner.setValueFactory(
                new SpinnerValueFactory.IntegerSpinnerValueFactory(0, 9999, 0));
        existing.ifPresent(book -> {
            titleLabel.setText("Edit book");
            isbnField.setText(book.isbn());
            isbnField.setDisable(true);
            titleField.setText(book.title());
            authorField.setText(book.author());
            genreField.setText(book.genre() == null ? "" : book.genre());
            publisherField.setText(book.publisher() == null ? "" : book.publisher());
            subjectField.setText(book.subject() == null ? "" : book.subject());
            yearSpinner.getValueFactory().setValue(
                    book.publicationYear() == null ? 0 : book.publicationYear());
            copiesSpinner.getValueFactory().setValue(book.totalCopies());
        });
    }

    @FXML
    private void save() {
        int totalCopies = copiesSpinner.getValue();
        int availableCopies = existing
                .map(book -> book.availableCopies() + (totalCopies - book.totalCopies()))
                .orElse(totalCopies);
        availableCopies = Math.max(0, Math.min(availableCopies, totalCopies));
        Integer year = yearSpinner.getValue();
        if (year == null || year == 0) {
            year = null;
        }
        Book book = new Book(
                isbnField.getText(),
                titleField.getText(),
                authorField.getText(),
                totalCopies,
                availableCopies,
                genreField.getText(),
                year,
                publisherField.getText(),
                subjectField.getText());
        Task<Book> saveTask = new Task<>() {
            @Override
            protected Book call() throws Exception {
                return existing.isPresent()
                        ? catalog.update(actor, book)
                        : catalog.add(actor, book);
            }
        };
        saveButton.disableProperty().bind(saveTask.runningProperty());
        errorLabel.setText("Saving…");
        saveTask.setOnSucceeded(ignored -> {
            onSaved.accept(saveTask.getValue());
            close();
        });
        saveTask.setOnFailed(ignored ->
                errorLabel.setText(saveTask.getException().getMessage()));
        Thread worker = new Thread(saveTask, "catalog-write");
        worker.setDaemon(true);
        worker.start();
    }

    @FXML
    private void cancel() {
        close();
    }

    private void close() {
        Stage stage = (Stage) saveButton.getScene().getWindow();
        stage.close();
    }
}
