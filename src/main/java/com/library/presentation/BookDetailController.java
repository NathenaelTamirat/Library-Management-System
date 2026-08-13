package com.library.presentation;

import com.library.domain.Book;
import javafx.fxml.FXML;
import javafx.scene.control.Label;
import javafx.stage.Stage;

public final class BookDetailController {
    private final Book book;

    @FXML
    private Label isbnLabel;
    @FXML
    private Label titleLabel;
    @FXML
    private Label authorLabel;
    @FXML
    private Label genreLabel;
    @FXML
    private Label yearLabel;
    @FXML
    private Label totalLabel;
    @FXML
    private Label availableLabel;

    public BookDetailController(Book book) {
        this.book = book;
    }

    @FXML
    private void initialize() {
        isbnLabel.setText(book.isbn());
        titleLabel.setText(book.title());
        authorLabel.setText(book.author());
        genreLabel.setText(book.genre() == null ? "—" : book.genre());
        yearLabel.setText(book.publicationYear() == null
                ? "—"
                : book.publicationYear().toString());
        totalLabel.setText(Integer.toString(book.totalCopies()));
        availableLabel.setText(Integer.toString(book.availableCopies()));
    }

    @FXML
    private void close() {
        ((Stage) isbnLabel.getScene().getWindow()).close();
    }
}
