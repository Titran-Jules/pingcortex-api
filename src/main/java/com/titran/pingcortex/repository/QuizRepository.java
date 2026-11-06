package com.titran.pingcortex.repository;

import com.titran.pingcortex.dto.response.QuizOptionAfterAttempt;
import com.titran.pingcortex.dto.response.QuizOptionResponse;
import com.titran.pingcortex.dto.response.QuizQuestionAfterAttempt;
import com.titran.pingcortex.dto.response.QuizQuestionResponse;
import com.titran.pingcortex.exception.DataAccessException;
import com.titran.pingcortex.model.Difficulty;
import com.titran.pingcortex.util.ManagedConnection;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.Array;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.*;

@Repository
@AllArgsConstructor
public class QuizRepository {
    private final DataSource dataSource;

    public Optional<QuizQuestionAfterAttempt> findQuizQuestionById(UUID userId, UUID quizQuestionId) {
        String sql = """
            SELECT qq.id, qq.concept_id, qq.question_text, qq.difficulty
            FROM quiz_question qq
            JOIN concept c ON c.id = qq.concept_id
            JOIN course co ON co.id = c.course_id
            WHERE qq.id = ? AND co.user_id = ?
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, quizQuestionId);
            stmt.setObject(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (!rs.next()) {
                    return Optional.empty();
                }
                UUID id = rs.getObject("id", UUID.class);
                UUID conceptId = rs.getObject("concept_id", UUID.class);
                String questionText = rs.getString("question_text");
                Difficulty difficulty = Difficulty.valueOf(rs.getString("difficulty"));
                List<QuizOptionAfterAttempt> options = findQuizOptionsWithCorrectFlag(id);
                return Optional.of(new QuizQuestionAfterAttempt(id, conceptId, questionText, difficulty, options));
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find quiz question", e);
        }
    }

    private List<QuizOptionAfterAttempt> findQuizOptionsWithCorrectFlag(UUID questionId) {
        String sql = """
            SELECT id, text, is_correct
            FROM quiz_option
            WHERE question_id = ?
        """;
        List<QuizOptionAfterAttempt> options = new ArrayList<>();
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, questionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    options.add(new QuizOptionAfterAttempt(
                            rs.getObject("id", UUID.class),
                            rs.getString("text"),
                            rs.getBoolean("is_correct")
                    ));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find quiz options", e);
        }
        return options;
    }

    public QuizQuestionResponse createQuizQuestion(UUID conceptId, String questionText, Difficulty difficulty) {
        String sql = """
            INSERT INTO quiz_question (id, concept_id, question_text, difficulty)
            VALUES (?, ?, ?, ?::difficulty_list)
            RETURNING id, question_text, difficulty;
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            UUID quizId = UUID.randomUUID();
            stmt.setObject(1, quizId);
            stmt.setObject(2, conceptId);
            stmt.setString(3, questionText);
            stmt.setString(4, difficulty.name());
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new QuizQuestionResponse(quizId, questionText, difficulty, new ArrayList<>());
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert quiz_question", e);
        }
    }

    public QuizOptionResponse createQuizOption(UUID questionId, String text, boolean isCorrect) {
        String sql = """
            INSERT INTO quiz_option (id, question_id, text, is_correct)
            VALUES (?, ?, ?, ?)
            RETURNING id, question_id, text, is_correct;
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            UUID optionId = UUID.randomUUID();
            stmt.setObject(1, optionId);
            stmt.setObject(2, questionId);
            stmt.setString(3, text);
            stmt.setBoolean(4, isCorrect);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return new QuizOptionResponse(optionId, text);
                } else {
                    return null;
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert quiz_option", e);
        }
    }

    public int countByConceptId(UUID conceptId) {
        String sql = """
            SELECT COUNT(*) AS count
            FROM quiz_question
            WHERE concept_id = ?;
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, conceptId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? rs.getInt("count") : 0;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to count questions", e);
        }
    }

    public List<QuizQuestionResponse> selectQuestionsForQuiz(UUID userId, UUID conceptId, Difficulty difficulty, int count) {
        String sql = """
            SELECT qq.*,
                CASE
                    WHEN EXISTS (SELECT 1 FROM quiz_attempt qa WHERE qa.question_id = qq.id AND qa.user_id = ? AND qa.is_correct = FALSE
                                AND NOT EXISTS (SELECT 1 FROM quiz_attempt qa2 WHERE qa2.question_id = qq.id AND qa2.user_id = ? AND qa2.is_correct = TRUE))
                        THEN 0
                    WHEN NOT EXISTS (SELECT 1 FROM quiz_attempt qa WHERE qa.question_id = qq.id AND qa.user_id = ?)
                        THEN 1
                    ELSE 2
                END AS priority,
            (SELECT MAX(created_at) FROM quiz_attempt qa WHERE qa.question_id = qq.id AND qa.user_id = ?) AS last_attempt_at
            FROM quiz_question qq
            WHERE qq.concept_id = ? AND qq.difficulty = ?::difficulty_list
            ORDER BY priority ASC, last_attempt_at ASC NULLS FIRST
            LIMIT ?;
        """;
        List<QuizQuestionResponse> responses = new ArrayList<>();
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, userId);
            stmt.setObject(2, userId);
            stmt.setObject(3, userId);
            stmt.setObject(4, userId);
            stmt.setObject(5, conceptId);
            stmt.setString(6, difficulty.name());
            stmt.setInt(7, count);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    responses.add(quizQuestionRowMapper(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to select quiz_question", e);
        }
        return responses;
    }

    public List<QuizOptionResponse> findQuizOptions(UUID questionId) {
        String sql = """
            SELECT id, text
            FROM quiz_option
            WHERE question_id = ?
        """;
        List<QuizOptionResponse> responses = new ArrayList<>();
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, questionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    responses.add(quizOptionRowMapper(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to select quiz_option", e);
        }
        return responses;
    }

    public Map<UUID, List<QuizOptionResponse>> findQuizOptionsByQuestionIds(List<UUID> questionIds) {
        if (questionIds.isEmpty()) {
            return Map.of();
        }
        String sql = """
        SELECT id, question_id, text
        FROM quiz_option
        WHERE question_id = ANY(?)
    """;
        Map<UUID, List<QuizOptionResponse>> optionsByQuestion = new HashMap<>();
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            Array questionIdsArray = connection.get().createArrayOf("uuid", questionIds.toArray());
            stmt.setArray(1, questionIdsArray);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    UUID questionId = rs.getObject("question_id", UUID.class);
                    QuizOptionResponse option = quizOptionRowMapper(rs);
                    optionsByQuestion.computeIfAbsent(questionId, k -> new ArrayList<>()).add(option);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to select quiz_option", e);
        }
        return optionsByQuestion;
    }

    public int findOrCreateMasteryLevel(UUID userId, UUID conceptId) {
        String upsertSql = """
            INSERT INTO user_concept_mastery (id, user_id, concept_id, mastery_level)
            VALUES (?, ?, ?, 0)
            ON CONFLICT (user_id, concept_id) DO NOTHING
            RETURNING mastery_level;
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(upsertSql)
        ) {
            stmt.setObject(1, UUID.randomUUID());
            stmt.setObject(2, userId);
            stmt.setObject(3, conceptId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("mastery_level");
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to upsert user_concept_mastery", e);
        }
        String selectSql = """
            SELECT mastery_level FROM user_concept_mastery
            WHERE user_id = ? AND concept_id = ?;
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(selectSql)
        ) {
            stmt.setObject(1, userId);
            stmt.setObject(2, conceptId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return rs.getInt("mastery_level");
                }
                throw new DataAccessException("Mastery row missing after conflict", null);
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to select user_concept_mastery", e);
        }
    }

    private QuizQuestionResponse quizQuestionRowMapper(ResultSet rs) throws SQLException {
        return new QuizQuestionResponse(
                rs.getObject("id", UUID.class),
                rs.getString("question_text"),
                Difficulty.valueOf(rs.getString("difficulty")),
                new ArrayList<>()
        );
    }

    private QuizOptionResponse quizOptionRowMapper(ResultSet rs) throws SQLException {
        return new QuizOptionResponse(
                rs.getObject("id", UUID.class),
                rs.getString("text")
        );
    }
}
