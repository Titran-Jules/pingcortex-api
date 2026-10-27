package com.titran.pingcortex.repository;

import com.titran.pingcortex.dto.response.ChatMessagesResponse;
import com.titran.pingcortex.dto.response.ChatSessionResponse;
import com.titran.pingcortex.dto.response.CourseResponse;
import com.titran.pingcortex.exception.DataAccessException;
import com.titran.pingcortex.model.AnalysisStatus;
import com.titran.pingcortex.model.Role;
import com.titran.pingcortex.util.ManagedConnection;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@AllArgsConstructor
public class ChatAiRepository {
    private final DataSource dataSource;

    public ChatSessionResponse createSession(UUID userId, UUID courseId) {
        String sql = """
            INSERT INTO chat_session (id, user_id, course_id) VALUES (?, ?, ?)
            RETURNING id, course_id, created_at
        """;
        ChatSessionResponse response = null;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            UUID sessionId = UUID.randomUUID();
            stmt.setObject(1, sessionId);
            stmt.setObject(2, userId);
            stmt.setObject(3, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    response = new ChatSessionResponse(
                            rs.getObject("id", UUID.class),
                            rs.getObject("course_id", UUID.class),
                            rs.getTimestamp("created_at").toInstant()
                    );
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert a new chat session", e);
        }
        return response;
    }

    public List<ChatSessionResponse> findAllSessionsByCourseId(UUID userId, UUID courseId) {
        String sql = """
            SELECT s.id, s.course_id, s.created_at
            FROM chat_session s
            JOIN course c ON s.course_id = c.id
            WHERE s.user_id = ? AND s.course_id = ?
            ORDER BY s.created_at DESC
        """;
        List<ChatSessionResponse> sessions = new ArrayList<>();
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, userId);
            stmt.setObject(2, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    sessions.add(new ChatSessionResponse(
                            rs.getObject("id", UUID.class),
                            rs.getObject("course_id", UUID.class),
                            rs.getObject("created_at", OffsetDateTime.class).toInstant()
                    ));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find all chat sessions of this course", e);
        }
        return sessions;
    }

    public ChatMessagesResponse insertNewMessage(UUID sessionId, Role role, String content) {
        String sql = """
            INSERT INTO chat_message (id, session_id, role, content) VALUES (?, ?, ?, ?)
            RETURNING id, role, content, created_at
        """;
        ChatMessagesResponse response = null;
        try  (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            UUID messageId = UUID.randomUUID();
            stmt.setObject(1, messageId);
            stmt.setObject(2, sessionId);
            stmt.setString(3, role.name());
            stmt.setString(4, content);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    response = messageRowMapper(rs);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to insert a new chat message", e);
        }
        return response;
    }

    public List<ChatMessagesResponse> findAllMessagesBySessionId(UUID userId, UUID sessionId) {
        String sql = """
            SELECT m.id, m.role, m.content, m.created_at
            FROM chat_message m
            JOIN chat_session s ON m.session_id = s.id
            WHERE s.user_id = ? AND m.session_id = ?
            ORDER BY m.created_at ASC
        """;
        List<ChatMessagesResponse> messages = new ArrayList<>();
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, userId);
            stmt.setObject(2, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    messages.add(messageRowMapper(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find all messages of this course", e);
        }
        return messages;
    }

    public Optional<CourseResponse> findCourseBySessionId(UUID userId, UUID sessionId) {
        String sql = """
            SELECT c.id, c.title, c.description, c.is_active, c.analysis_status, c.created_at
            FROM course c
            JOIN chat_session s ON s.course_id = c.id
            WHERE s.user_id = ? AND s.id = ?
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, userId);
            stmt.setObject(2, sessionId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ?
                        Optional.of(new CourseResponse(
                                rs.getObject("id", UUID.class),
                                rs.getString("title"),
                                rs.getString("description"),
                                rs.getBoolean("is_active"),
                                AnalysisStatus.valueOf(rs.getString("analysis_status")),
                                rs.getTimestamp("created_at").toInstant()
                        )) :
                        Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find course by session id", e);
        }
    }

    private ChatMessagesResponse messageRowMapper(ResultSet rs) throws SQLException {
        return new ChatMessagesResponse(
                rs.getObject("id", UUID.class),
                Role.valueOf(rs.getString("role")),
                rs.getString("content"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
