package com.library.service;

import com.library.data.RecommendationRepository;
import com.library.domain.BookRecommendation;
import com.library.domain.User;
import com.library.security.AuthorizationService;
import com.library.security.Permission;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

public final class RecommendationService {
    private static final int MAX_LIMIT = 20;

    private final RecommendationRepository recommendations;
    private final AuthorizationService authorization;

    public RecommendationService(
            RecommendationRepository recommendations,
            AuthorizationService authorization) {
        this.recommendations = recommendations;
        this.authorization = authorization;
    }

    public List<BookRecommendation> recommendFor(User actor, UUID memberId, int limit)
            throws SQLException {
        authorize(actor, memberId);
        if (limit < 1 || limit > MAX_LIMIT) {
            throw new IllegalArgumentException("Recommendation limit must be between 1 and 20");
        }

        Map<String, BookRecommendation> ranked = new LinkedHashMap<>();
        recommendations.findByReadingHistory(memberId, limit).forEach(
                recommendation -> ranked.putIfAbsent(
                        recommendation.book().isbn(), recommendation));

        int remaining = limit - ranked.size();
        if (remaining > 0) {
            recommendations.findPopularUnread(memberId, limit).forEach(
                    recommendation -> ranked.putIfAbsent(
                            recommendation.book().isbn(), recommendation));
        }
        return new ArrayList<>(ranked.values()).subList(0, Math.min(limit, ranked.size()));
    }

    private void authorize(User actor, UUID memberId) {
        if (actor.id().equals(memberId)) {
            return;
        }
        authorization.require(actor, Permission.MANAGE_LOANS);
    }
}
