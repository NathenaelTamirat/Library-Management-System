package com.library.service;

import static org.junit.jupiter.api.Assertions.*;

import com.library.data.RecommendationRepository;
import com.library.domain.Book;
import com.library.domain.BookRecommendation;
import com.library.domain.Librarian;
import com.library.domain.Member;
import com.library.security.AuthorizationService;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class RecommendationServiceTest {
    @Test
    void combinesPersonalizedAndPopularResultsWithoutDuplicates() throws Exception {
        Book personalizedBook = book("1111111111", "Robots", "Isaac Asimov");
        Book popularBook = book("2222222222", "Dune", "Frank Herbert");
        RecommendationRepository repository = new StubRecommendations(
                List.of(recommendation(personalizedBook, 101, "Known author")),
                List.of(
                        recommendation(personalizedBook, 5, "Popular"),
                        recommendation(popularBook, 4, "Popular")));
        RecommendationService service =
                new RecommendationService(repository, new AuthorizationService());
        Member member =
                new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);

        List<BookRecommendation> result =
                service.recommendFor(member, member.id(), 2);

        assertEquals(List.of(personalizedBook, popularBook),
                result.stream().map(BookRecommendation::book).toList());
        assertEquals("Known author", result.get(0).reason());
    }

    @Test
    void coldStartUsesPopularFallback() throws Exception {
        Book popularBook = book("2222222222", "Dune", "Frank Herbert");
        RecommendationService service = new RecommendationService(
                new StubRecommendations(
                        List.of(), List.of(recommendation(popularBook, 8, "Popular"))),
                new AuthorizationService());
        Member member =
                new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);

        assertEquals(List.of(popularBook),
                service.recommendFor(member, member.id(), 5).stream()
                        .map(BookRecommendation::book)
                        .toList());
    }

    @Test
    void membersCannotInspectAnotherMembersRecommendations() {
        RecommendationService service = new RecommendationService(
                new StubRecommendations(List.of(), List.of()),
                new AuthorizationService());
        Member actor =
                new Member(UUID.randomUUID(), "Ada", "ada@example.edu", "hash", 5);

        assertThrows(SecurityException.class,
                () -> service.recommendFor(actor, UUID.randomUUID(), 5));
    }

    @Test
    void librariansCanAssistMembersWithRecommendations() throws Exception {
        RecommendationService service = new RecommendationService(
                new StubRecommendations(List.of(), List.of()),
                new AuthorizationService());
        Librarian librarian = new Librarian(
                UUID.randomUUID(), "Lib", "lib@example.edu", "hash", "AUD-2", false);

        assertTrue(service.recommendFor(librarian, UUID.randomUUID(), 5).isEmpty());
    }

    private static Book book(String isbn, String title, String author) {
        return new Book(isbn, title, author, 1, 1);
    }

    private static BookRecommendation recommendation(Book book, long score, String reason) {
        return new BookRecommendation(book, score, reason);
    }

    private record StubRecommendations(
            List<BookRecommendation> personalized,
            List<BookRecommendation> popular) implements RecommendationRepository {
        @Override
        public List<BookRecommendation> findByReadingHistory(UUID memberId, int limit) {
            return personalized;
        }

        @Override
        public List<BookRecommendation> findPopularUnread(UUID memberId, int limit) {
            return popular;
        }
    }
}
