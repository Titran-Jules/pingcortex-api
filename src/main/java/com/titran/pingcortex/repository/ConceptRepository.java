package com.titran.pingcortex.repository;

import com.titran.pingcortex.dto.response.ConceptMasteryResponse;
import com.titran.pingcortex.dto.response.ConceptResponse;
import com.titran.pingcortex.exception.DataAccessException;
import com.titran.pingcortex.model.ConceptStatus;
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
public class ConceptRepository {
    private final DataSource dataSource;

    public boolean belongsToUser(UUID userId, UUID conceptId) {
        String sql = """
            SELECT 1
            FROM concept c
            JOIN course co ON co.id = c.course_id
            WHERE c.id = ? AND co.user_id = ?
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, conceptId);
            stmt.setObject(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check concept ownership", e);
        }
    }

    public boolean isCoveredOrAnticipated(UUID conceptId) {
        String sql = """
            SELECT 1
            FROM concept
            WHERE id = ? AND coverage_status != 'NOT_COVERED'
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, conceptId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to check concept status", e);
        }
    }

    public ConceptResponse create(UUID courseId, String name, String description, int position) {
        String sql = """
            INSERT INTO concept (id, course_id, name, description, position)
            VALUES (?, ?, ?, ?, ?)
            RETURNING id, name, description, position, coverage_status, suggested_coverage
        """;
        ConceptResponse concept = null;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            UUID conceptId = UUID.randomUUID();
            stmt.setObject(1, conceptId);
            stmt.setObject(2, courseId);
            stmt.setString(3, name);
            stmt.setString(4, description);
            stmt.setInt(5, position);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    concept = conceptMapper(rs);
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to create the concept", e);
        }
        return concept;
    }

    public Optional<ConceptResponse> findById(UUID conceptId) {
        String sql = """
            SELECT id, name, description, position, coverage_status, suggested_coverage
            FROM concept WHERE id = ?
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        )  {
            stmt.setObject(1, conceptId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(conceptMapper(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find the concept", e);
        }
    }

    public List<ConceptResponse> findAllByCourseId(UUID userId, UUID courseId) {
        String sql = """
            SELECT c.id, c.name, c.description, c.position, c.coverage_status, c.suggested_coverage, m.mastery_level
            FROM concept c
            JOIN course co ON c.course_id = co.id
            LEFT JOIN user_concept_mastery m ON m.concept_id = c.id AND m.user_id = co.user_id
            WHERE c.course_id = ? AND co.user_id = ?
            ORDER BY position
        """;
        List<ConceptResponse> conceptResponses = new ArrayList<>();
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, courseId);
            stmt.setObject(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    conceptResponses.add(conceptWithMasteryMapper(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find all concepts", e);
        }
        return conceptResponses;
    }

    public List<ConceptResponse> findAllNotCoveredByCourseId(UUID userId, UUID courseId) {
        String sql = """
            SELECT c.id, c.name, c.description, c.position ,c.coverage_status, c.suggested_coverage
            FROM concept c
            JOIN course co ON c.course_id = co.id
            WHERE c.course_id = ? AND co.user_id = ? AND c.coverage_status != 'COVERED'
            ORDER BY position
        """;
        List<ConceptResponse> conceptResponses = new ArrayList<>();
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, courseId);
            stmt.setObject(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    conceptResponses.add(conceptMapper(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find all concepts", e);
        }
        return conceptResponses;
    }

    public Optional<ConceptResponse> confirmCoverage(UUID userId, UUID courseId, UUID conceptId) {
        return updateCoverageStatus(userId, courseId, conceptId, "COVERED");
    }

    public Optional<ConceptResponse> revertCoverage(UUID userId, UUID courseId, UUID conceptId) {
        return updateCoverageStatus(userId, courseId, conceptId, "NOT_COVERED");
    }

    public Optional<ConceptResponse> advanceCoverage(UUID userId, UUID courseId, UUID conceptId) {
        return updateCoverageStatus(userId, courseId, conceptId, "ANTICIPATED_COVERED");
    }

    private Optional<ConceptResponse> updateCoverageStatus(UUID userId, UUID courseId, UUID conceptId, String newStatus) {
        String sql = """
            UPDATE concept c
            SET coverage_status = ?::coverage_status_list
            FROM course co
            WHERE c.course_id = co.id
              AND c.id = ?
              AND c.course_id = ?
              AND co.user_id = ?
            RETURNING c.id, c.name, c.description, c.position, c.coverage_status, c.suggested_coverage,
                (SELECT m.mastery_level FROM user_concept_mastery m
                 WHERE m.concept_id = c.id AND m.user_id = ?) AS mastery_level
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setString(1, newStatus);
            stmt.setObject(2, conceptId);
            stmt.setObject(3, courseId);
            stmt.setObject(4, userId);
            stmt.setObject(5, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                if (rs.next()) {
                    return Optional.of(conceptWithMasteryMapper(rs));
                }
                return Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update concept coverage status", e);
        }
    }

    public void deleteAllByCourseId(UUID courseId) {
        String sql = "DELETE FROM concept WHERE course_id = ?";
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, courseId);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to delete all concepts of course", e);
        }
    }

    public Optional<ConceptMasteryResponse> findConceptMastery(UUID userId, UUID conceptId) {
        String sql = """
            SELECT concept_id, mastery_level, last_reviewed_at
            FROM user_concept_mastery
            WHERE user_id = ? AND concept_id = ?
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, userId);
            stmt.setObject(2, conceptId);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(conceptMasteryResponseRowMapper(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find concept mastery", e);
        }
    }

    public Optional<UUID> findPreviousConceptId(UUID courseId, int position) {
        String sql = """
            SELECT id
            FROM concept
            WHERE course_id = ? AND position = ? - 1
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, courseId);
            stmt.setInt(2, position);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(rs.getObject("id", UUID.class)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find previous concept id", e);
        }
    }

    private void updateSuggestedCoverage(UUID userId, UUID courseId, UUID conceptId, UUID materialId, boolean isAccepted) {
        String sql = """
            UPDATE concept c
            SET suggested_coverage = ?,
                suggested_from_material_id = ?
            FROM course co
            WHERE c.course_id = co.id
                AND c.id = ?
                AND co.course_id = ?
                AND co.user_id = ?
                AND c.coverage_status != 'COVERED'
            RETURNING c.id, c.name, c.description, c.position, c.coverage_status
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, isAccepted);
            stmt.setObject(2, materialId);
            stmt.setObject(3, conceptId);
            stmt.setObject(4, courseId);
            stmt.setObject(5, userId);
            int result = stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to update concept suggested_coverage", e);
        }
    }

    public void addSuggestionCoverage(UUID userId, UUID courseId, UUID conceptId, UUID materialId) {
        updateSuggestedCoverage(userId, courseId, conceptId, materialId, true);
    }

    public void rejectSuggestionCoverage(UUID userId, UUID courseId, UUID conceptId, UUID materialId) {
        updateSuggestedCoverage(userId, courseId, conceptId, materialId, false);
    }

    public List<ConceptResponse> findAllSuggestedCoverageConcept(UUID userId, UUID courseId) {
        String sql = """
            SELECT  c.id, c.name, c.description, c.position, c.coverage_status, c.suggested_coverage
            FROM concept c
            JOIN course co
            ON c.course_id = co.id
            WHERE c.course_id = ? AND co.user_id = ? AND c.suggested_coverage = TRUE AND c.coverage_status != 'COVERED'
        """;
        List<ConceptResponse> conceptResponses = new ArrayList<>();
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, courseId);
            stmt.setObject(2, userId);
            try (ResultSet rs = stmt.executeQuery()) {
                while(rs.next()) {
                    conceptResponses.add(conceptMapper(rs));
                }
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find concept suggested_coverage", e);
        }
        return conceptResponses;
    }

    private ConceptResponse conceptMapper(ResultSet rs) throws SQLException {
        return new ConceptResponse(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("position"),
                ConceptStatus.valueOf(rs.getString("coverage_status")),
                rs.getBoolean("suggested_coverage"),
                null
        );
    }

    private ConceptResponse conceptWithMasteryMapper(ResultSet rs) throws SQLException {
        return new ConceptResponse(
                rs.getObject("id", UUID.class),
                rs.getString("name"),
                rs.getString("description"),
                rs.getInt("position"),
                ConceptStatus.valueOf(rs.getString("coverage_status")),
                rs.getBoolean("suggested_coverage"),
                rs.getObject("mastery_level", Integer.class)
        );
    }

    private ConceptMasteryResponse conceptMasteryResponseRowMapper(ResultSet rs) throws SQLException {
        return new ConceptMasteryResponse(
                rs.getObject("concept_id", UUID.class),
                rs.getInt("mastery_level"),
                rs.getObject("last_reviewed_at", OffsetDateTime.class).toInstant()
        );
    }
}
