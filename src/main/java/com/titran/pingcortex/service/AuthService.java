package com.titran.pingcortex.service;

import com.titran.pingcortex.dto.request.LoginRequest;
import com.titran.pingcortex.dto.request.RegisterRequest;
import com.titran.pingcortex.dto.response.LoginResult;
import com.titran.pingcortex.dto.response.UserResponse;
import com.titran.pingcortex.exception.InvalidCredentialsException;
import com.titran.pingcortex.exception.UserNotFoundException;
import com.titran.pingcortex.model.User;
import com.titran.pingcortex.repository.AuthRepository;
import com.titran.pingcortex.repository.UserRepository;
import com.titran.pingcortex.security.JwtService;
import com.titran.pingcortex.security.PasswordService;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.Locale;

@Service
@AllArgsConstructor
public class AuthService {
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;
    private static final String DUMMY_BCRYPT_HASH = "$2a$10$N9qo8uLOickgx2ZMRZoMyeIjZAgcfl7p92ldGxad68LJZdL17lhWy";

    private static String normalizeEmail(String email) {
        return email.trim().toLowerCase(Locale.ROOT);
    }

    public UserResponse register(RegisterRequest request) {
        String passwordHash = passwordService.hash(request.password());
        return authRepository.register(
                normalizeEmail(request.email()),
                request.name().trim(),
                passwordHash,
                request.level()
        );
    }

    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(normalizeEmail(request.email())).orElse(null);
        if (user == null) {
            passwordService.matches(request.password(), DUMMY_BCRYPT_HASH);
            throw new InvalidCredentialsException();
        }
        if (!passwordService.matches(request.password(), user.passwordHash())) {
            throw new InvalidCredentialsException();
        }
        String accessToken = jwtService.generateAccessToken(user);
        String refreshToken = refreshTokenService.issue(user.id());
        return new LoginResult(accessToken, refreshToken, new UserResponse(user.id(),  user.email(), user.name(), user.level(), user.alertThreshold(), user.createdAt()));
    }

    public LoginResult refresh(String rawRefreshToken) {
        RefreshTokenService.RotationResult rotation = refreshTokenService.validateAndRotate(rawRefreshToken);
        User user = userRepository.findByIdWithCredentials(rotation.userId()).
                orElseThrow(UserNotFoundException::new);
        String newAccessToken = jwtService.generateAccessToken(user);
        return new LoginResult(newAccessToken, rotation.newRawToken(), new UserResponse(user.id(),  user.email(), user.name(), user.level(), user.alertThreshold(), user.createdAt()));
    }

    public void logout(String rawRefreshToken) {
        refreshTokenService.revoke(rawRefreshToken);
    }
}
