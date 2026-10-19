package com.titran.pingcortex.security;

import com.titran.pingcortex.dto.request.AccessTokenClaims;
import com.titran.pingcortex.exception.InvalidTokenException;
import com.titran.pingcortex.repository.UserRepository;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.List;

@Component
public class JwtAuthenticationFilter extends OncePerRequestFilter {
    public static final String USER_ID_ATTRIBUTE = "authenticatedUserId";

    private static final List<String> PUBLIC_PATHS = List.of(
            "/health",
            "/auth/register",
            "/auth/login",
            "/auth/refresh"
    );

    private static final AntPathMatcher PATH_MATCHER = new AntPathMatcher();

    private final JwtService jwtService;
    private final UserRepository userRepository;

    public JwtAuthenticationFilter(JwtService jwtService, UserRepository userRepository) {
        this.jwtService = jwtService;
        this.userRepository = userRepository;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain
    ) throws ServletException, IOException {
        String path = request.getServletPath();
        boolean isPublic = PUBLIC_PATHS.stream().anyMatch(pattern -> PATH_MATCHER.match(pattern, path));
        if (isPublic) {
            filterChain.doFilter(request, response);
            return;
        }
        String header = request.getHeader("Authorization");
        if (header == null || !header.startsWith("Bearer ")) {
            rejectUnauthorized(response, "Missing or malformed Authorization header");
            return;
        }
        String token = header.substring("Bearer ".length());

        try {
            AccessTokenClaims claims = jwtService.parseAndValidate(token);
            int currentTokenVersion = userRepository.findTokenVersion(claims.id())
                    .orElseThrow(() -> new InvalidTokenException("User not longer exists", null));
            if (claims.tokenVersion() != currentTokenVersion) {
                throw new InvalidTokenException("Token has been revoked", null);
            }
            request.setAttribute(USER_ID_ATTRIBUTE, claims.id());
        } catch (InvalidTokenException e) {
            rejectUnauthorized(response, e.getMessage());
            return;
        }
        filterChain.doFilter(request, response);
    }

    private void rejectUnauthorized(HttpServletResponse response, String message) throws IOException {
        response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
        response.setContentType("application/json");
        response.getWriter().write(
                """
                    {"code": "UNAUTHORIZED", "message": "%s"}
                """.formatted(message)
        );
    }
}
