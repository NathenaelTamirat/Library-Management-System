package com.library.service;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.BookRepository;
import com.library.data.LoanTransactionManager;
import com.library.domain.Book;
import com.library.domain.Librarian;
import com.library.domain.Loan;
import com.library.domain.ReturnReceipt;
import com.library.security.AuthorizationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class CatalogServiceTest {
    @Test
    void deleteRefusesWhenOpenLoansExist() throws Exception {
        RecordingBooks books = new RecordingBooks();
        RecordingLoans loans = new RecordingLoans();
        Book book = new Book("9780134685991", "Effective Java", "Joshua Bloch", 2, 1);
        books.save(book);
        loans.openByIsbn.put(book.isbn(), 1);
        RecordingAudit audits = new RecordingAudit();
        CatalogService catalog = new CatalogService(
                books, loans, new AuthorizationService(), new AuditService(audits));
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Libby", "lib@example.edu", "hash", "desk", false);

        IllegalStateException failure = assertThrows(
                IllegalStateException.class,
                () -> catalog.delete(librarian, book.isbn()));

        assertTrue(failure.getMessage().contains("open loan"));
        assertTrue(books.stored.containsKey(book.isbn()));
        assertTrue(audits.actions.isEmpty());
    }

    @Test
    void updateRefusesInventoryBelowCheckedOutCopies() throws Exception {
        RecordingBooks books = new RecordingBooks();
        RecordingLoans loans = new RecordingLoans();
        Book book = new Book("9780134685991", "Effective Java", "Joshua Bloch", 2, 0);
        books.save(book);
        loans.openByIsbn.put(book.isbn(), 2);
        CatalogService catalog = new CatalogService(
                books,
                loans,
                new AuthorizationService(),
                new AuditService(new RecordingAudit()));
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Libby", "lib@example.edu", "hash", "desk", false);

        assertThrows(IllegalStateException.class, () -> catalog.update(
                librarian,
                new Book(book.isbn(), book.title(), book.author(), 1, 0)));
        assertThrows(IllegalStateException.class, () -> catalog.update(
                librarian,
                new Book(book.isbn(), book.title(), book.author(), 2, 1)));
    }

    private static final class RecordingAudit implements com.library.data.AuditRepository {
        private final java.util.ArrayList<String> actions = new java.util.ArrayList<>();

        @Override
        public void record(Optional<UUID> userId, String action, String details) {
            actions.add(action);
        }
        @Override
        public java.util.List<com.library.domain.AuditEntry> findRecent(int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.library.domain.AuditEntry> findByAction(String action, int limit) {
            return java.util.List.of();
        }

        @Override
        public java.util.List<com.library.domain.AuditEntry> findByUser(java.util.UUID userId, int limit) {
            return java.util.List.of();
        }

    }

    private static final class RecordingBooks implements BookRepository {
        private final Map<String, Book> stored = new HashMap<>();

        @Override
        public Optional<Book> findByIsbn(String isbn) {
            return Optional.ofNullable(stored.get(isbn));
        }

        @Override
        public List<Book> search(String query) {
            return List.copyOf(stored.values());
        }

        @Override
        public void save(Book book) {
            stored.put(book.isbn(), book);
        }

        @Override
        public void update(Book book) {
            stored.put(book.isbn(), book);
        }

        @Override
        public void delete(String isbn) {
            stored.remove(isbn);
        }
    }

    private static final class RecordingLoans implements LoanTransactionManager {
        private final Map<String, Integer> openByIsbn = new HashMap<>();

        @Override
        public Loan checkout(
                UUID userId, String isbn, LocalDate checkoutDate, LocalDate dueDate, int borrowingLimit) {
            throw new UnsupportedOperationException();
        }

        @Override
        public ReturnReceipt returnLoan(UUID loanId, LocalDate returnDate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public Loan renew(UUID loanId, LocalDate newDueDate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int markOverdueBefore(LocalDate asOfDate) {
            return 0;
        }

        @Override
        public Loan markLost(UUID loanId, BigDecimal replacementFine, LocalDate issuedDate) {
            throw new UnsupportedOperationException();
        }

        @Override
        public int countOpenLoansByIsbn(String isbn) {
            return openByIsbn.getOrDefault(isbn, 0);
        }

        @Override
        public List<Loan> findOpenLoansByUser(UUID userId) {
            return List.of();
        }

        @Override
        public List<Loan> findOverdueLoans() {
            return List.of();
        }

        @Override
        public Optional<Loan> findById(UUID loanId) {
            return Optional.empty();
        }

        @Override
        public Optional<Loan> findActiveLoanByIsbn(String isbn) {
            return Optional.empty();
        }
    }
}
