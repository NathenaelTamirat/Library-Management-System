package com.library.service;

import com.library.data.BookRepository;
import com.library.data.FineRepository;
import com.library.data.LoanTransactionManager;
import com.library.domain.Book;
import com.library.domain.Fine;
import com.library.domain.Loan;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import com.library.util.CsvExporter;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;

public final class ExportService {
    private final BookRepository books;
    private final LoanTransactionManager loans;
    private final FineRepository fines;
    private final AuthorizationService authorization;

    public ExportService(
            BookRepository books,
            LoanTransactionManager loans,
            FineRepository fines,
            AuthorizationService authorization) {
        this.books = books;
        this.loans = loans;
        this.fines = fines;
        this.authorization = authorization;
    }

    public String catalogCsv(User actor) throws SQLException {
        authorization.require(actor, Permission.MANAGE_CATALOG);
        List<Book> catalog = books.search("");
        List<List<String>> rows = new ArrayList<>();
        for (Book book : catalog) {
            rows.add(List.of(
                    book.isbn(),
                    book.title(),
                    book.author(),
                    book.genre() == null ? "" : book.genre(),
                    book.publicationYear() == null ? "" : book.publicationYear().toString(),
                    Integer.toString(book.totalCopies()),
                    Integer.toString(book.availableCopies())));
        }
        return CsvExporter.toCsv(
                List.of("isbn", "title", "author", "genre", "year", "total", "available"),
                rows);
    }

    public String overdueCsv(User actor) throws SQLException {
        authorization.require(actor, Permission.MANAGE_LOANS);
        List<Loan> overdue = loans.findOverdueLoans();
        List<List<String>> rows = new ArrayList<>();
        for (Loan loan : overdue) {
            rows.add(List.of(
                    loan.id().toString(),
                    loan.userId().toString(),
                    loan.isbn(),
                    loan.dueDate().toString(),
                    loan.status().name()));
        }
        return CsvExporter.toCsv(
                List.of("loan_id", "user_id", "isbn", "due_date", "status"),
                rows);
    }

    public String unpaidFinesCsv(User actor) throws SQLException {
        authorization.require(actor, Permission.MANAGE_LOANS);
        List<Fine> unpaid = fines.findUnpaid();
        List<List<String>> rows = new ArrayList<>();
        for (Fine fine : unpaid) {
            rows.add(List.of(
                    fine.id().toString(),
                    fine.loanId().toString(),
                    fine.amount().toPlainString(),
                    fine.issuedDate().toString()));
        }
        return CsvExporter.toCsv(
                List.of("fine_id", "loan_id", "amount", "issued_date"),
                rows);
    }
}
