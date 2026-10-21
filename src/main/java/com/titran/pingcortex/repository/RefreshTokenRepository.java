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
import java.sql.Timestamp;
import java.util.Optional;
import java.util.UUID;

@AllArgsConstructor
@Repository
public class RefreshTokenRepository {
    private DataSource dataSource;

    public void save(RefreshToken token) {
        String sql = """
            INSERT INTO refresh_token (id, user_id, token_hash, expires_at, revoked)
            VALUES (?, ?, ?, ?, ?)
        """;
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)
        ) {
            stmt.setObject(1, token.id());
            stmt.setObject(2, token.userId());
            stmt.setString(3, token.tokenHash());
            stmt.setTimestamp(4, Timestamp.from(token.expiresAt()));
            stmt.setBoolean(5, token.revoked());
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to save refresh token", e);
        }
    }

    public Optional<RefreshToken> findByTokenHash(String tokenHash) {
        String sql = """
            SELECT id, user_id, token_hash, expires_at, revoked, created_at
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

    public void revoke(UUID id) {
        String sql = "UPDATE refresh_token SET revoked = TRUE WHERE id = ?";
        try (ManagedConnection connection = ManagedConnection.open(dataSource);
             PreparedStatement stmt = connection.get().prepareStatement(sql)) {
            stmt.setObject(1, id);
            stmt.executeUpdate();
        } catch (SQLException e) {
            throw new DataAccessException("Failed to revoke refresh token", e);
        }
    }

    private RefreshToken refreshTokenRowMapper(ResultSet rs) throws SQLException {
        return new RefreshToken(
                rs.getObject("id", UUID.class),
                rs.getObject("user_id", UUID.class),
                rs.getString("token_hash"),
                rs.getTimestamp("expires_at").toInstant(),
                rs.getBoolean("revoked"),
                rs.getTimestamp("created_at").toInstant()
        );
    }
}
