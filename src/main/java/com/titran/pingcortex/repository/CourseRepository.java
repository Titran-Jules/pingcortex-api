package com.titran.pingcortex.repository;

import com.titran.pingcortex.dto.request.CourseRequest;
import com.titran.pingcortex.dto.request.CourseUpdate;
import com.titran.pingcortex.dto.request.CourseUpdateActive;
import com.titran.pingcortex.dto.response.CourseResponse;
import com.titran.pingcortex.exception.DataAccessException;
import com.titran.pingcortex.model.AnalysisStatus;
import com.titran.pingcortex.util.ManagedConnection;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
@AllArgsConstructor
public class CourseRepository {
    private final DataSource dataSource;

    public CourseResponse createCourse(UUID userId, CourseRequest request) {
        String sql = """
            INSERT INTO course (id, user_id, title, description)
            VALUES (?, ?, ?, ?)
            RETURNING id, title, description, is_active, analysis_status, created_at
        """;
        CourseResponse course = null;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            UUID courseId = UUID.randomUUID();
            stmt.setObject(1, courseId);
            stmt.setObject(2, userId);
            stmt.setString(3, request.title());
            stmt.setString(4, request.description());

            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    course = courseRowMapper(rs);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create course", e);
        }
        return course;
    }

    public List<CourseResponse> findAll(UUID userId, Boolean isActive) {
        String sql = "";
        if (isActive != null) {
            sql = """
                SELECT id, title, description, is_active, analysis_status, created_at
                FROM course
                WHERE is_active = ? AND user_id = ?
            """;
        } else {
            sql = """
                SELECT id, title, description, is_active, analysis_status, created_at
                FROM course
                WHERE user_id = ?
            """;
        }
        List<CourseResponse> courses = new ArrayList<>();
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            if (isActive != null) {
                stmt.setBoolean(1, isActive);
                stmt.setObject(2, userId);
            } else {
                stmt.setObject(1, userId);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    courses.add(courseRowMapper(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to retrieve courses", e);
        }
        return courses;
    }

    public Optional<CourseResponse> findById(UUID userId, UUID courseId) {
        String sql = """
            SELECT id, title, description, is_active, analysis_status, created_at
            FROM course
            WHERE user_id = ? AND id = ?
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, userId);
            stmt.setObject(2, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(courseRowMapper(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to get course", e);
        }
    }

    public Optional<CourseResponse> updateCourse(UUID userId, UUID courseId, CourseUpdate request) {
        String sql = """
            UPDATE course
            SET title = ?,
                description = ?
            WHERE user_id = ? AND id = ?
            RETURNING id, title, description, is_active, analysis_status, created_at
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setString(1, request.title());
            stmt.setString(2, request.description());
            stmt.setObject(3, userId);
            stmt.setObject(4, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(courseRowMapper(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update course", e);
        }
    }

    public Optional<CourseResponse> updateCourseActive(UUID userId, UUID courseId, CourseUpdateActive request) {
        String sql = """
            UPDATE course
            SET is_active = ?
            WHERE user_id = ? AND id = ?
            RETURNING id, title, description, is_active, analysis_status, created_at
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setBoolean(1, request.isActive());
            stmt.setObject(2, userId);
            stmt.setObject(3, courseId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(courseRowMapper(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update course", e);
        }
    }

    public boolean updateAnalysisStatus(UUID courseId, AnalysisStatus status) {
        String sql = """
            UPDATE course
            SET analysis_status = ?
            WHERE id = ?
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setString(1, status.name());
            stmt.setObject(2, courseId);
            int result = stmt.executeUpdate();
            if (result != 1) {
                return false;
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update status course", e);
        }
        return true;
    }

    private CourseResponse courseRowMapper(ResultSet rs) throws SQLException {
        return new CourseResponse(
                UUID.fromString(rs.getString("id")),
                rs.getString("title"),
                rs.getString("description"),
                rs.getBoolean("is_active"),
                AnalysisStatus.valueOf(rs.getString("analysis_status")),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
