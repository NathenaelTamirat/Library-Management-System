package com.library.service;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

import com.library.data.BookRepository;
import com.library.data.FineRepository;
import com.library.data.LoanTransactionManager;
import com.library.domain.Book;
import com.library.domain.Fine;
import com.library.domain.Librarian;
import com.library.domain.Loan;
import com.library.domain.Member;
import com.library.domain.ReturnReceipt;
import com.library.security.AuthorizationService;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class ExportServiceTest {
    @Test
    void staffCanExportCatalogOverdueAndUnpaidFines() throws Exception {
        Book book = new Book("9780134685991", "Effective Java", "Joshua Bloch", 1, 1, "Tech", 2018);
        Loan overdue = new Loan(
                UUID.randomUUID(),
                UUID.randomUUID(),
                book.isbn(),
                LocalDate.of(2026, 7, 1),
                LocalDate.of(2026, 7, 10));
        overdue.markOverdue();
        Fine fine = new Fine(
                UUID.randomUUID(), overdue.id(), new BigDecimal("2.50"), false, LocalDate.of(2026, 7, 20));
        ExportService exports = new ExportService(
                new BookRepository() {
                    @Override
                    public Optional<Book> findByIsbn(String isbn) {
                        return Optional.of(book);
                    }

                    @Override
                    public List<Book> search(String query) {
                        return List.of(book);
                    }

                    @Override
                    public void save(Book book) {
                    }

                    @Override
                    public void update(Book book) {
                    }

                    @Override
                    public void delete(String isbn) {
                    }
                },
                new OverdueOnly(overdue),
                new UnpaidOnly(fine),
                new AuthorizationService());
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Libby", "lib@example.edu", "hash", "desk", false);

        assertTrue(exports.catalogCsv(librarian).contains("Effective Java"));
        assertTrue(exports.overdueCsv(librarian).contains(overdue.isbn()));
        assertTrue(exports.unpaidFinesCsv(librarian).contains("2.50"));
        assertThrows(
                SecurityException.class,
                () -> exports.catalogCsv(new Member(
                        UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5)));
    }

    private static final class OverdueOnly implements LoanTransactionManager {
        private final Loan overdue;

        private OverdueOnly(Loan overdue) {
            this.overdue = overdue;
        }

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
            return 0;
        }

        @Override
        public List<Loan> findOpenLoansByUser(UUID userId) {
            return List.of();
        }

        @Override
        public List<Loan> findOverdueLoans() {
            return List.of(overdue);
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

    private static final class UnpaidOnly implements FineRepository {
        private final Fine fine;

        private UnpaidOnly(Fine fine) {
            this.fine = fine;
        }

        @Override
        public Optional<Fine> findByLoanId(UUID loanId) {
            return Optional.empty();
        }

        @Override
        public List<Fine> findUnpaidByUser(UUID userId) {
            return List.of();
        }

        @Override
        public List<Fine> findUnpaid() {
            return List.of(fine);
        }

        @Override
        public void markPaid(UUID fineId) {
        }

        @Override
        public void waive(UUID fineId) {
        }
    }
}
