package com.library.data;

import com.library.domain.BookRecommendation;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;

public interface RecommendationRepository {
    List<BookRecommendation> findByReadingHistory(UUID memberId, int limit) throws SQLException;

    List<BookRecommendation> findPopularUnread(UUID memberId, int limit) throws SQLException;
}
