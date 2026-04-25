package ru.mescat.security.util;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.mescat.security.dto.AuthResponse;
import ru.mescat.security.service.JwtService;

import java.time.Duration;
import java.time.Instant;
import java.util.Optional;

@Component
public class AuthCookieService {

    private final JwtService jwtService;
    private final boolean forceSecureCookies;

    public AuthCookieService(JwtService jwtService,
                             @Value("${security.cookies.force-secure:false}") boolean forceSecureCookies) {
        this.jwtService = jwtService;
        this.forceSecureCookies = forceSecureCookies;
    }

    public void addAuthCookies(HttpServletResponse response, HttpServletRequest request, AuthResponse tokens) {
        boolean secure = isSecureRequest(request);

        ResponseCookie accessCookie = buildCookie("access_token", tokens.getAccessToken(), secure, resolveMaxAge(tokens.getAccessToken()));
        ResponseCookie refreshCookie = buildCookie("refresh_token", tokens.getRefreshToken(), secure, resolveMaxAge(tokens.getRefreshToken()));

        response.addHeader(HttpHeaders.SET_COOKIE, accessCookie.toString());
        response.addHeader(HttpHeaders.SET_COOKIE, refreshCookie.toString());
    }

    public void clearAuthCookies(HttpServletResponse response, HttpServletRequest request) {
        boolean secure = isSecureRequest(request);
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("access_token", "", secure, Duration.ZERO).toString());
        response.addHeader(HttpHeaders.SET_COOKIE, buildCookie("refresh_token", "", secure, Duration.ZERO).toString());
    }

    public String extractCookieValue(HttpServletRequest request, String cookieName) {
        Cookie[] cookies = request.getCookies();
        if (cookies == null) {
            return null;
        }

        for (Cookie cookie : cookies) {
            if (cookieName.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }

        return null;
    }

    public Optional<String> resolveAnyToken(HttpServletRequest request) {
        String accessToken = extractCookieValue(request, "access_token");
        if (accessToken != null && !accessToken.isBlank()) {
            return Optional.of(accessToken);
        }

        String refreshToken = extractCookieValue(request, "refresh_token");
        if (refreshToken != null && !refreshToken.isBlank()) {
            return Optional.of(refreshToken);
        }

        String authHeader = request.getHeader(HttpHeaders.AUTHORIZATION);
        if (authHeader != null && authHeader.startsWith("Bearer ")) {
            return Optional.of(authHeader.substring(7));
        }

        return Optional.empty();
    }

    private ResponseCookie buildCookie(String name, String value, boolean secure, Duration maxAge) {
        return ResponseCookie.from(name, value)
                .httpOnly(true)
                .secure(secure)
                .sameSite("Strict")
                .path("/")
                .maxAge(maxAge)
                .build();
    }

    private Duration resolveMaxAge(String token) {
        Instant expiresAt = jwtService.extractExpiration(token).toInstant();
        Duration ttl = Duration.between(Instant.now(), expiresAt);
        return ttl.isNegative() ? Duration.ZERO : ttl;
    }

    private boolean isSecureRequest(HttpServletRequest request) {
        if (forceSecureCookies) {
            return true;
        }
        String forwardedProto = request.getHeader("X-Forwarded-Proto");
        if (forwardedProto != null && !forwardedProto.isBlank()) {
            return "https".equalsIgnoreCase(forwardedProto);
        }
        return request.isSecure();
    }
}
