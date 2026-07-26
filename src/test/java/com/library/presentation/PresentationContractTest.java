package com.library.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.BookRepository;
import com.library.data.LoanTransactionManager;
import com.library.domain.Book;
import com.library.domain.Librarian;
import com.library.domain.Loan;
import com.library.domain.Member;
import com.library.domain.ReturnReceipt;
import com.library.security.AuthorizationService;
import com.library.service.AuditService;
import com.library.service.CatalogService;
import java.io.InputStream;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import javax.xml.parsers.DocumentBuilderFactory;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;

class PresentationContractTest {
    @Test
    void catalogServiceNormalizesInputAndReturnsRepositoryResults() throws Exception {
        Book expected = new Book("9780134685991", "Effective Java", "Joshua Bloch", 2, 1);
        RecordingRepository repository = new RecordingRepository(expected);
        CatalogService catalog = new CatalogService(
                repository, new NoOpenLoans(), new AuthorizationService(), silentAudit());

        assertEquals(List.of(expected), catalog.search("  Bloch  "));
        assertEquals("Bloch", repository.lastQuery);
    }

    @Test
    void catalogWriteOperationsAreAllowedForLibrariansAndDeniedForMembers() throws Exception {
        Book expected = new Book("9780134685991", "Effective Java", "Joshua Bloch", 2, 1);
        RecordingRepository repository = new RecordingRepository(expected);
        CatalogService catalog = new CatalogService(
                repository, new NoOpenLoans(), new AuthorizationService(), silentAudit());
        Member member = new Member(UUID.randomUUID(), "Member", "member@example.edu", "hash", 5);
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Librarian", "librarian@example.edu", "hash", "AUD-1", false);
        Book added = new Book("9780321356680", "Effective Java 2", "Joshua Bloch", 1, 1);

        assertThrows(SecurityException.class, () -> catalog.add(member, added));
        assertEquals(added, catalog.add(librarian, added));
        assertEquals(added, repository.saved);

        Book renamed = new Book(expected.isbn(), "Effective Java 3", "Joshua Bloch", 2, 1);
        assertThrows(SecurityException.class, () -> catalog.update(member, renamed));
        assertEquals(renamed, catalog.update(librarian, renamed));
        assertEquals(renamed, repository.updated);
    }

    @Test
    void catalogDeletionIsAllowedForLibrariansAndDeniedForMembers() throws Exception {
        Book expected = new Book("9780134685991", "Effective Java", "Joshua Bloch", 2, 1);
        RecordingRepository repository = new RecordingRepository(expected);
        CatalogService catalog = new CatalogService(
                repository, new NoOpenLoans(), new AuthorizationService(), silentAudit());
        Member member = new Member(UUID.randomUUID(), "Member", "member@example.edu", "hash", 5);
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Librarian", "librarian@example.edu", "hash", "AUD-1", false);

        assertThrows(SecurityException.class, () -> catalog.delete(member, expected.isbn()));
        catalog.delete(librarian, expected.isbn());

        assertEquals(expected.isbn(), repository.deletedIsbn);
    }

    @Test
    void fxmlIsWellFormedAndWiresSearchControls() throws Exception {
        Document document = parse("/view/catalog.fxml");

        assertEquals("BorderPane", document.getDocumentElement().getNodeName());
        assertEquals(1, document.getElementsByTagName("TextField").getLength());
        assertEquals(1, document.getElementsByTagName("TableView").getLength());
        assertEquals(4, document.getElementsByTagName("TableColumn").getLength());
        assertEquals(10, document.getElementsByTagName("Button").getLength());
        assertEquals("#search",
                document.getElementsByTagName("Button").item(0)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#showRecommendations",
                document.getElementsByTagName("Button").item(1)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#borrowSelected",
                document.getElementsByTagName("Button").item(2)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#returnSelected",
                document.getElementsByTagName("Button").item(3)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#showFines",
                document.getElementsByTagName("Button").item(4)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#showMyLoans",
                document.getElementsByTagName("Button").item(5)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#addBook",
                document.getElementsByTagName("Button").item(6)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#editSelected",
                document.getElementsByTagName("Button").item(7)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#deleteSelected",
                document.getElementsByTagName("Button").item(8)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#signOut",
                document.getElementsByTagName("Button").item(9)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertNotNull(getClass().getResource("/view/library.css"));
        assertNotNull(getClass().getResource("/view/book-editor.fxml"));
        assertNotNull(getClass().getResource("/view/recommendations.fxml"));
        assertNotNull(getClass().getResource("/view/fines.fxml"));
        assertNotNull(getClass().getResource("/view/my-loans.fxml"));
    }

    @Test
    void myLoansFxmlWiresReturnByLoanIdActions() throws Exception {
        Document document = parse("/view/my-loans.fxml");

        assertEquals("VBox", document.getDocumentElement().getNodeName());
        assertEquals(1, document.getElementsByTagName("ListView").getLength());
        assertEquals(4, document.getElementsByTagName("Button").getLength());
        assertEquals("#refresh",
                document.getElementsByTagName("Button").item(0)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#returnSelected",
                document.getElementsByTagName("Button").item(1)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#renewSelected",
                document.getElementsByTagName("Button").item(2)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#close",
                document.getElementsByTagName("Button").item(3)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
    }

    @Test
    void finesFxmlWiresRefreshPayAndCloseActions() throws Exception {
        Document document = parse("/view/fines.fxml");

        assertEquals("VBox", document.getDocumentElement().getNodeName());
        assertEquals(1, document.getElementsByTagName("ListView").getLength());
        assertEquals(3, document.getElementsByTagName("Button").getLength());
        assertEquals("#refresh",
                document.getElementsByTagName("Button").item(0)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#paySelected",
                document.getElementsByTagName("Button").item(1)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#close",
                document.getElementsByTagName("Button").item(2)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
    }

    @Test
    void bookEditorFxmlWiresSaveAction() throws Exception {
        Document document = parse("/view/book-editor.fxml");

        assertEquals("VBox", document.getDocumentElement().getNodeName());
        assertEquals(3, document.getElementsByTagName("TextField").getLength());
        assertEquals("#save",
                document.getElementsByTagName("Button").item(0)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
    }

    @Test
    void recommendationsFxmlWiresRefreshBorrowAndCloseActions() throws Exception {
        Document document = parse("/view/recommendations.fxml");

        assertEquals("VBox", document.getDocumentElement().getNodeName());
        assertEquals(1, document.getElementsByTagName("ListView").getLength());
        assertEquals(3, document.getElementsByTagName("Button").getLength());
        assertEquals("#refresh",
                document.getElementsByTagName("Button").item(0)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#borrowSelected",
                document.getElementsByTagName("Button").item(1)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#close",
                document.getElementsByTagName("Button").item(2)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
    }

    @Test
    void loginFxmlWiresCredentialFieldsAndSignInAction() throws Exception {
        Document document = parse("/view/login.fxml");

        assertEquals("StackPane", document.getDocumentElement().getNodeName());
        assertEquals(1, document.getElementsByTagName("PasswordField").getLength());
        assertEquals("#login",
                document.getElementsByTagName("Button").item(0)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
    }

    private Document parse(String resource) throws Exception {
        try (InputStream input = getClass().getResourceAsStream(resource)) {
            assertNotNull(input);
            DocumentBuilderFactory factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            return factory.newDocumentBuilder().parse(input);
        }
    }

    private static AuditService silentAudit() {
        return new AuditService(new com.library.data.AuditRepository() {
            @Override
            public void record(Optional<UUID> userId, String action, String details) {
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
            public java.util.List<com.library.domain.AuditEntry> findByUser(UUID userId, int limit) {
                return java.util.List.of();
            }
        });
    }

    private static final class NoOpenLoans implements LoanTransactionManager {
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

    private static final class RecordingRepository implements BookRepository {
        private final Book result;
        private String lastQuery;
        private String deletedIsbn;
        private Book saved;
        private Book updated;

        private RecordingRepository(Book result) {
            this.result = result;
        }

        @Override
        public Optional<Book> findByIsbn(String isbn) {
            if (saved != null && saved.isbn().equals(isbn)) {
                return Optional.of(saved);
            }
            if (updated != null && updated.isbn().equals(isbn)) {
                return Optional.of(updated);
            }
            return result.isbn().equals(isbn) ? Optional.of(result) : Optional.empty();
        }

        @Override
        public List<Book> search(String query) {
            lastQuery = query;
            return List.of(result);
        }

        @Override
        public void save(Book book) {
            saved = book;
        }

        @Override
        public void update(Book book) {
            updated = book;
        }

        @Override
        public void delete(String isbn) {
            deletedIsbn = isbn;
        }
    }
}
