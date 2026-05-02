package com.travel.travelplanner.config;

import java.io.IOException;

import org.springframework.util.StringUtils;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

import org.springframework.core.annotation.Order;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import com.travel.travelplanner.auth.service.AuthService;

@Component
@Order(1)
public class UserIdFilter extends OncePerRequestFilter {

    private static final String HEADER_USER_ID = "X-User-Id";
    private static final String HEADER_AUTH = "Authorization";

    private final AuthService authService;

    public UserIdFilter(AuthService authService) {
        this.authService = authService;
    }

    @Override
    protected void doFilterInternal(
            @NonNull HttpServletRequest request,
            @NonNull HttpServletResponse response,
            @NonNull FilterChain filterChain) throws ServletException, IOException {
        String path = request.getRequestURI();
        if (!path.startsWith("/api/") || "OPTIONS".equalsIgnoreCase(request.getMethod()) || path.startsWith("/api/auth")) {
            filterChain.doFilter(request, response);
            return;
        }

        String bearer = request.getHeader(HEADER_AUTH);
        String userIdFromToken = resolveBearerUserId(bearer);
        if (StringUtils.hasText(userIdFromToken)) {
            request.setAttribute("userId", userIdFromToken);
            filterChain.doFilter(request, response);
            return;
        }

        // Backward-compatible MVP fallback (local UUID from FE)
        String userId = request.getHeader(HEADER_USER_ID);
        if (userId == null || userId.isBlank()) {
            response.setStatus(HttpServletResponse.SC_UNAUTHORIZED);
            response.setContentType("application/json");
            response.getWriter().write("{\"message\":\"Unauthorized: Authorization Bearer token (or X-User-Id) is required\"}");
            return;
        }
        request.setAttribute("userId", userId.trim());
        filterChain.doFilter(request, response);
    }

    private String resolveBearerUserId(String header) {
        if (header == null) return null;
        String h = header.trim();
        if (!h.regionMatches(true, 0, "Bearer ", 0, "Bearer ".length())) return null;
        String token = h.substring("Bearer ".length()).trim();
        return authService.authenticateToken(token);
    }
}
