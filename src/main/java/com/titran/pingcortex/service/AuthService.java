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

import java.util.UUID;

@Service
@AllArgsConstructor
public class AuthService {
    private final AuthRepository authRepository;
    private final UserRepository userRepository;
    private final PasswordService passwordService;
    private final JwtService jwtService;
    private final RefreshTokenService refreshTokenService;

    public UserResponse register(RegisterRequest request) {
        String passwordHash = passwordService.hash(request.password());
        return authRepository.register(
                request.email(),
                request.name(),
                passwordHash,
                request.level()
        );
    }

    public LoginResult login(LoginRequest request) {
        User user = userRepository.findByEmail(request.email()).
                orElseThrow(UserNotFoundException::new);
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

    public void logout(UUID authenticatedUserId, String rawRefreshToken) {
        refreshTokenService.revoke(authenticatedUserId, rawRefreshToken);
    }
}
