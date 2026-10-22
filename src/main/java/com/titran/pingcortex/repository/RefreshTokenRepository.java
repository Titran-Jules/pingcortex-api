package com.titran.pingcortex.repository;

import com.titran.pingcortex.dto.request.RefreshToken;
import com.titran.pingcortex.exception.DataAccessException;
import com.titran.pingcortex.util.ManagedConnection;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Repository;

import javax.sql.DataSource;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.time.Instant;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
@Repository
public class RefreshTokenRepository {
    private DataSource dataSource;

    private static OffsetDateTime utc(Instant instant) {
        return OffsetDateTime.ofInstant(instant, ZoneOffset.UTC);
    }

    public void save(RefreshToken token) {
        String sql = """
            INSERT INTO refresh_token (id, user_id, token_hash, expires_at, revoked, family_id)
            VALUES (?, ?, ?, ?, ?, ?)
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, token.id());
            stmt.setObject(2, token.userId());
            stmt.setString(3, token.tokenHash());
            stmt.setObject(4, utc(token.expiresAt()));
            stmt.setBoolean(5, token.revoked());
            stmt.setObject(6, token.familyId());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save refresh token", e);
        }
    }

    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        String sql = """
            SELECT id, user_id, token_hash, expires_at, revoked, revoked_at, family_id, created_at
            FROM refresh_token
            WHERE token_hash = ?
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
            PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setString(1, tokenHash);
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(refreshTokenRowMapper(rs)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to find refresh token", e);
        }
    }

    public Optional<UUID> revokeFamily(UUID familyId) {
        String sql = """
            UPDATE refresh_token
            SET revoked = TRUE, revoked_at = now()
            WHERE family_id = ? AND revoked = FALSE
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)) {
            stmt.setObject(1, familyId);
            return stmt.executeUpdate() > 0 ? Optional.of(familyId) : Optional.empty();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to revoke refresh token family", e);
        }
    }

    public void revokeByTokenHash(String tokenHash) {
        String sql = """
            UPDATE refresh_token
            SET revoked = TRUE, revoked_at = now()
            WHERE token_hash = ?
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)) {
            stmt.setString(1, tokenHash);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to revoke refresh token", e);
        }
    }

    public Optional<UUID> rotate(String oldTokenHash, String newTokenHash, Instant newExpiresAt) {
        String sql = """
            WITH revoked AS (
                UPDATE refresh_token
                SET revoked = TRUE, revoked_at = now()
                WHERE token_hash = ?
                  AND revoked = FALSE
                  AND expires_at > now()
                RETURNING user_id, family_id
            )
            INSERT INTO refresh_token (id, user_id, token_hash, expires_at, revoked, family_id)
            SELECT ?, user_id, ?, ?, FALSE, family_id
            FROM revoked
            RETURNING user_id
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)) {
            stmt.setString(1, oldTokenHash);
            stmt.setObject(2, UUID.randomUUID());
            stmt.setString(3, newTokenHash);
            stmt.setObject(4, utc(newExpiresAt));
            try (ResultSet rs = stmt.executeQuery()) {
                return rs.next() ? Optional.of(rs.getObject("user_id", UUID.class)) : Optional.empty();
            }
        } catch (SQLException e) {
            throw new DataAccessException("Failed to rotate refresh token", e);
        }
    }

    private RefreshToken refreshTokenRowMapper(ResultSet rs) throws SQLException {
        return new RefreshToken(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("token_hash"),
                rs.getObject("expires_at", OffsetDateTime.class).toInstant(),
                rs.getBoolean("revoked"),
                rs.getObject("revoked_at", OffsetDateTime.class) == null
                        ? null
                        : rs.getObject("revoked_at", OffsetDateTime.class).toInstant(),
                rs.getObject("family_id", UUID.class),
                rs.getObject("created_at", OffsetDateTime.class).toInstant()
        );
    }
}
