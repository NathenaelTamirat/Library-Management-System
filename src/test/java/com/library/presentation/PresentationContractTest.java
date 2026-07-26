package com.library.presentation;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.BookRepository;
import com.library.domain.Book;
import com.library.domain.Librarian;
import com.library.domain.Member;
import com.library.security.AuthorizationService;
import com.library.service.CatalogService;
import java.io.InputStream;
import java.sql.SQLException;
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
        CatalogService catalog = new CatalogService(repository, new AuthorizationService());

        assertEquals(List.of(expected), catalog.search("  Bloch  "));
        assertEquals("Bloch", repository.lastQuery);
    }

    @Test
    void catalogDeletionIsAllowedForLibrariansAndDeniedForMembers() throws Exception {
        Book expected = new Book("9780134685991", "Effective Java", "Joshua Bloch", 2, 1);
        RecordingRepository repository = new RecordingRepository(expected);
        CatalogService catalog = new CatalogService(repository, new AuthorizationService());
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
        assertEquals(3, document.getElementsByTagName("Button").getLength());
        assertEquals("#search",
                document.getElementsByTagName("Button").item(0)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#borrowSelected",
                document.getElementsByTagName("Button").item(1)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertEquals("#deleteSelected",
                document.getElementsByTagName("Button").item(2)
                        .getAttributes().getNamedItem("onAction").getNodeValue());
        assertNotNull(getClass().getResource("/view/library.css"));
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

    private static final class RecordingRepository implements BookRepository {
        private final Book result;
        private String lastQuery;
        private String deletedIsbn;

        private RecordingRepository(Book result) {
            this.result = result;
        }

        @Override
        public Optional<Book> findByIsbn(String isbn) {
            return Optional.of(result);
        }

        @Override
        public List<Book> search(String query) {
            lastQuery = query;
            return List.of(result);
        }

        @Override
        public void save(Book book) throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void update(Book book) throws SQLException {
            throw new UnsupportedOperationException();
        }

        @Override
        public void delete(String isbn) throws SQLException {
            deletedIsbn = isbn;
        }
    }
}
