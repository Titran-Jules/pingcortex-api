package com.titran.pingcortex.repository;

import com.titran.pingcortex.dto.response.CourseProgressResponse;
import com.titran.pingcortex.exception.DataAccessException;
import com.titran.pingcortex.model.AnalysisStatus;
import com.titran.pingcortex.util.ManagedConnection;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.math.BigDecimal;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

@Repository
@AllArgsConstructor
public class DashboardRepository {
    private final DataSource dataSource;

    public List<CourseProgressResponse> findCourseProgress(UUID userId, Boolean isActive) {
        String sql = """
            SELECT c.id, c.title, c.is_active, c.analysis_status,
                   count(DISTINCT cp.id) AS total_concepts,
                   count(DISTINCT cp.id) FILTER (WHERE cp.coverage_status = 'COVERED') AS covered_concepts,
                   count(DISTINCT cp.id) FILTER (WHERE cp.coverage_status = 'ANTICIPATED_COVERED') AS in_progress_concepts,
                   count(DISTINCT cp.id) FILTER (WHERE m.mastery_level >= 80) AS mastered_concepts,
                   count(DISTINCT rs.id) FILTER (WHERE rs.next_review_at <= now()) AS due_review_count,
                   coalesce(round(avg(m.mastery_level)), 0) AS average_mastery
            FROM course c
            LEFT JOIN concept cp ON cp.course_id = c.id
            LEFT JOIN user_concept_mastery m ON m.concept_id = cp.id AND m.user_id = c.user_id
            LEFT JOIN review_schedule rs ON rs.concept_id = cp.id AND rs.user_id = c.user_id
            WHERE c.user_id = ?
        """ + (isActive != null ? " AND c.is_active = ?\n" : "") + """
            GROUP BY c.id, c.title, c.is_active, c.analysis_status
            ORDER BY max(c.created_at) DESC
        """;
        List<CourseProgressResponse> progressList = new ArrayList<>();
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, userId);
            if (isActive != null) {
                stmt.setBoolean(2, isActive);
            }
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    progressList.add(courseProgressRowMapper(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find course progress", e);
        }
        return progressList;
    }

    private CourseProgressResponse courseProgressRowMapper(ResultSet rs) throws SQLException {
        return new CourseProgressResponse(
                rs.getObject("id", UUID.class),
                rs.getString("title"),
                rs.getBoolean("is_active"),
                AnalysisStatus.valueOf(rs.getString("analysis_status")),
                rs.getInt("total_concepts"),
                rs.getInt("covered_concepts"),
                rs.getInt("in_progress_concepts"),
                rs.getInt("mastered_concepts"),
                rs.getBigDecimal("average_mastery").intValue(),
                rs.getInt("due_review_count")
        );
    }
}
