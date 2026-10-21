package com.titran.pingcortex.controller;

import com.titran.pingcortex.dto.request.LoginRequest;
import com.titran.pingcortex.dto.request.RefreshRequest;
import com.titran.pingcortex.dto.request.RegisterRequest;
import com.titran.pingcortex.dto.response.LoginResponse;
import com.titran.pingcortex.dto.response.LoginResult;
import com.titran.pingcortex.dto.response.UserResponse;
import com.titran.pingcortex.security.CurrentUser;
import com.titran.pingcortex.service.AuthService;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseCookie;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.Duration;
import java.util.List;
import java.util.UUID;

@RestController
public class AuthController {
    private final AuthService authService;
    private final boolean cookieSecure;
    private final List<String> allowedOrigins;

    public AuthController(
            AuthService authService,
            @Value("${app.cookie.secure:true}") boolean cookieSecure,
            @Value("${app.cors.allowed-origins:http://localhost:5173}") List<String> allowedOrigins
    ) {
        this.authService = authService;
        this.cookieSecure = cookieSecure;
        this.allowedOrigins = allowedOrigins;
    }

    @PostMapping("/auth/register")
    public ResponseEntity<UserResponse> registerUser(@RequestBody RegisterRequest registerRequest) {
        return ResponseEntity.status(HttpStatus.CREATED).body(authService.register(registerRequest));
    }

    @PostMapping("/auth/login")
    public ResponseEntity<LoginResponse> loginUser(
            @RequestBody LoginRequest loginRequest,
            @RequestHeader(value = "X-Client-Type", required = false) String clientType
    ) {
        LoginResult outcome = authService.login(loginRequest);
        boolean isMobile = "mobile".equalsIgnoreCase(clientType);
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();

        if (!isMobile) {
            ResponseCookie cookie = createRefreshTokenCookie(outcome.rawRefreshToken(), Duration.ofDays(7));
            responseBuilder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        String refreshTokenForBody = isMobile ? outcome.rawRefreshToken() : null;
        return responseBuilder.body(new LoginResponse(outcome.accessToken(), refreshTokenForBody, outcome.user()));
    }

    @PostMapping("/auth/refresh")
    public ResponseEntity<LoginResponse> refreshToken(
            @RequestBody(required = false) RefreshRequest refreshRequest,
            @CookieValue(name = "rt", required = false) String cookieRefreshToken,
            @RequestHeader(value = "X-Client-Type",  required = false) String clientType,
            HttpServletRequest request
    ) {
        validateOriginHeader(request);
        boolean isMobile = "mobile".equalsIgnoreCase(clientType);
        String rawToken = isMobile && refreshRequest != null ? refreshRequest.refreshToken() : cookieRefreshToken;

        if (rawToken == null || rawToken.isBlank()) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        LoginResult outcome = authService.refresh(rawToken);
        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok();
        if (!isMobile) {
            ResponseCookie cookie = createRefreshTokenCookie(outcome.rawRefreshToken(), Duration.ofDays(7));
            responseBuilder.header(HttpHeaders.SET_COOKIE, cookie.toString());
        }
        String refreshTokenForBody = isMobile ? outcome.rawRefreshToken() : null;
        return responseBuilder.body(new LoginResponse(outcome.accessToken(), refreshTokenForBody, outcome.user()));
    }

    @PostMapping("/auth/logout")
    public ResponseEntity<Void> logoutUser(
            @CookieValue(name = "rt", required = false) String cookieRefreshToken,
            @RequestBody(required = false) RefreshRequest refreshRequest,
            @RequestHeader(value = "X-Client-Type", required = false) String clientType,
            HttpServletRequest request
    ) {
        validateOriginHeader(request);
        UUID userId = CurrentUser.id(request);
        boolean isMobile = "mobile".equalsIgnoreCase(clientType);
        String rawToken = isMobile && refreshRequest != null ? refreshRequest.refreshToken() : cookieRefreshToken;
        if (rawToken != null && !rawToken.isBlank()) {
            authService.logout(userId, rawToken);
        }
        ResponseCookie deleteCookie = createRefreshTokenCookie("", Duration.ZERO);
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, deleteCookie.toString())
                .build();
    }

    private void validateOriginHeader(HttpServletRequest request) {
        String origin = request.getHeader("Origin");
        if (origin != null && !allowedOrigins.contains(origin)) {
            throw new SecurityException("Origin not allowed : " + origin);
        }
    }

    private ResponseCookie createRefreshTokenCookie(String tokenValue, Duration maxAge) {
        return ResponseCookie.from("rt", tokenValue)
                .httpOnly(true)
                .secure(cookieSecure)
                .sameSite("Strict")
                .path("/api/auth")
                .maxAge(maxAge)
                .build();
    }
}
