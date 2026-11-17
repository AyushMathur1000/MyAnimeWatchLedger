package com.myanimewatchledger.infrastructure.persistence;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.myanimewatchledger.application.port.RecommendationHistoryRepository;
import com.myanimewatchledger.domain.recommendation.*;

import java.sql.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public final class JdbcRecommendationHistoryRepository implements RecommendationHistoryRepository {
    private final DatabaseTransactionManager transactions;
    private final ObjectMapper json = new ObjectMapper();

    public JdbcRecommendationHistoryRepository(DatabaseTransactionManager transactions) {
        this.transactions = Objects.requireNonNull(transactions);
    }

    @Override
    public long recordRecommendation(long malId, String engineId, int engineVersion,
                                     String featureSnapshotJson, String recommendationJson, Instant createdAt) {
        if (malId <= 0 || engineVersion < 1) throw new IllegalArgumentException("Invalid recommendation identity.");
        if (engineId == null || engineId.isBlank()) throw new IllegalArgumentException("Engine ID is required.");
        validateObjectJson(featureSnapshotJson, "Feature snapshot");
        validateObjectJson(recommendationJson, "Recommendation snapshot");
        Objects.requireNonNull(createdAt, "createdAt");
        return transactions.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO recommendation_event
                        (mal_id, engine_id, engine_version, feature_snapshot_json, recommendation_json, created_at)
                    VALUES (?, ?, ?, ?, ?, ?)
                    """, Statement.RETURN_GENERATED_KEYS)) {
                statement.setLong(1, malId);
                statement.setString(2, engineId);
                statement.setInt(3, engineVersion);
                statement.setString(4, featureSnapshotJson);
                statement.setString(5, recommendationJson);
                statement.setLong(6, createdAt.toEpochMilli());
                statement.executeUpdate();
                try (ResultSet keys = statement.getGeneratedKeys()) {
                    if (!keys.next()) throw new SQLException("SQLite did not return a recommendation event ID.");
                    return keys.getLong(1);
                }
            }
        });
    }

    @Override
    public void recordFeedback(long eventId, RecommendationDimension dimension,
                               RecommendationFeedbackAction action, String selectedValue, Instant respondedAt) {
        if (eventId <= 0) throw new IllegalArgumentException("Event ID must be positive.");
        Objects.requireNonNull(dimension, "dimension");
        Objects.requireNonNull(action, "action");
        Objects.requireNonNull(respondedAt, "respondedAt");
        // Reuse the domain validation so accepted/modified and rejected/dismissed stay distinct.
        new RecommendationFeedback(dimension, action, selectedValue, respondedAt);
        transactions.withConnection(connection -> {
            try (PreparedStatement statement = connection.prepareStatement("""
                    INSERT INTO recommendation_feedback
                        (event_id, dimension, action, selected_value, responded_at)
                    VALUES (?, ?, ?, ?, ?)
                    ON CONFLICT(event_id, dimension) DO UPDATE SET
                        action = excluded.action,
                        selected_value = excluded.selected_value,
                        responded_at = excluded.responded_at
                    """)) {
                statement.setLong(1, eventId);
                statement.setString(2, dimension.name());
                statement.setString(3, action.name());
                statement.setString(4, selectedValue);
                statement.setLong(5, respondedAt.toEpochMilli());
                statement.executeUpdate();
            }
            return null;
        });
    }

    @Override
    public List<RecommendationHistoryEntry> findAll() {
        return transactions.withConnection(connection -> {
            List<EventData> rows = new ArrayList<>();
            try (PreparedStatement statement = connection.prepareStatement("""
                    SELECT event_id, mal_id, engine_id, engine_version, feature_snapshot_json,
                           recommendation_json, created_at
                    FROM recommendation_event ORDER BY created_at, event_id
                    """);
                 ResultSet result = statement.executeQuery()) {
                while (result.next()) {
                    rows.add(new EventData(result.getLong("event_id"), result.getLong("mal_id"),
                            result.getString("engine_id"), result.getInt("engine_version"),
                            result.getString("feature_snapshot_json"), result.getString("recommendation_json"),
                            Instant.ofEpochMilli(result.getLong("created_at"))));
                }
            }
            List<RecommendationHistoryEntry> events = new ArrayList<>();
            for (EventData row : rows) {
                events.add(new RecommendationHistoryEntry(row.eventId(), row.malId(), row.engineId(),
                        row.engineVersion(), row.featureSnapshotJson(), row.recommendationJson(),
                        row.createdAt(), findFeedback(connection, row.eventId())));
            }
            return List.copyOf(events);
        });
    }

    private List<RecommendationFeedback> findFeedback(Connection connection, long eventId) throws SQLException {
        List<RecommendationFeedback> feedback = new ArrayList<>();
        try (PreparedStatement statement = connection.prepareStatement("""
                SELECT dimension, action, selected_value, responded_at
                FROM recommendation_feedback WHERE event_id = ? ORDER BY dimension
                """)) {
            statement.setLong(1, eventId);
            try (ResultSet rows = statement.executeQuery()) {
                while (rows.next()) {
                    feedback.add(new RecommendationFeedback(
                            RecommendationDimension.valueOf(rows.getString("dimension")),
                            RecommendationFeedbackAction.valueOf(rows.getString("action")),
                            rows.getString("selected_value"),
                            Instant.ofEpochMilli(rows.getLong("responded_at"))));
                }
            }
        }
        return List.copyOf(feedback);
    }

    private record EventData(long eventId, long malId, String engineId, int engineVersion,
                             String featureSnapshotJson, String recommendationJson, Instant createdAt) { }

    private void validateObjectJson(String source, String label) {
        try {
            JsonNode node = json.readTree(source);
            if (node == null || !node.isObject()) throw new IllegalArgumentException(label + " must be a JSON object.");
        } catch (IllegalArgumentException failure) {
            throw failure;
        } catch (Exception failure) {
            throw new IllegalArgumentException(label + " is invalid JSON.", failure);
        }
    }
}
