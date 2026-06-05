package com.devsecops.usermgmt.filter;

import com.devsecops.usermgmt.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.bucket4j.Bandwidth;
import io.github.bucket4j.Bucket;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Servlet filter that enforces per-IP rate limits on sensitive auth endpoints
 * using Bucket4j token-bucket algorithm.
 *
 * <p>Rate limits (OWASP API4 — Lack of Resources &amp; Rate Limiting):
 * <ul>
 *   <li>POST /api/auth/login          — 5 requests / minute / IP</li>
 *   <li>POST /api/auth/register       — 3 requests / minute / IP</li>
 *   <li>POST /api/auth/forgot-password — 3 requests / minute / IP</li>
 * </ul>
 * Returns HTTP 429 with a JSON body when the limit is exceeded.</p>
 */
@Slf4j
@Component
@Order(1)
public class RateLimitingFilter extends OncePerRequestFilter {

    private static final String LOGIN_PATH            = "/api/auth/login";
    private static final String REGISTER_PATH         = "/api/auth/register";
    private static final String FORGOT_PASSWORD_PATH  = "/api/auth/forgot-password";
    private static final String REFRESH_PATH          = "/api/auth/refresh";

    private final RateLimitProperties rateLimitProperties;
    private final ObjectMapper objectMapper;

    /** Buckets keyed by "IP:endpoint" to isolate per-IP counters per route. */
    private final ConcurrentHashMap<String, Bucket> buckets = new ConcurrentHashMap<>();

    public RateLimitingFilter(RateLimitProperties rateLimitProperties,
                               ObjectMapper objectMapper) {
        this.rateLimitProperties = rateLimitProperties;
        this.objectMapper = objectMapper;
    }

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getServletPath();
        return !path.equals(LOGIN_PATH)
                && !path.equals(REGISTER_PATH)
                && !path.equals(FORGOT_PASSWORD_PATH)
                && !path.equals(REFRESH_PATH);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String ip   = extractClientIp(request);
        String path = request.getServletPath();
        String key  = ip + ":" + path;

        Bucket bucket = buckets.computeIfAbsent(key, k -> createBucket(path));

        if (bucket.tryConsume(1)) {
            filterChain.doFilter(request, response);
        } else {
            log.warn("Rate limit exceeded for IP={} path={}", ip, path);
            sendTooManyRequests(response, ip, path);
        }
    }

    private Bucket createBucket(String path) {
        RateLimitProperties.EndpointLimit limit = resolveLimit(path);
        Bandwidth bandwidth = Bandwidth.builder()
                .capacity(limit.getCapacity())
                .refillIntervally(limit.getRefillTokens(),
                        Duration.ofSeconds(limit.getRefillPeriodSeconds()))
                .build();
        return Bucket.builder().addLimit(bandwidth).build();
    }

    private RateLimitProperties.EndpointLimit resolveLimit(String path) {
        return switch (path) {
            case LOGIN_PATH    -> rateLimitProperties.getLogin();
            case REGISTER_PATH -> rateLimitProperties.getRegister();
            default            -> rateLimitProperties.getForgotPassword();
        };
    }

    /**
     * Extracts the real client IP, respecting common proxy headers.
     * Only the first value in X-Forwarded-For is trusted.
     */
    private String extractClientIp(HttpServletRequest request) {
        String xForwardedFor = request.getHeader("X-Forwarded-For");
        if (xForwardedFor != null && !xForwardedFor.isBlank()) {
            return xForwardedFor.split(",")[0].trim();
        }
        String xRealIp = request.getHeader("X-Real-IP");
        if (xRealIp != null && !xRealIp.isBlank()) {
            return xRealIp.trim();
        }
        return request.getRemoteAddr();
    }

    private void sendTooManyRequests(HttpServletResponse response,
                                     String ip, String path) throws IOException {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("error", "Too Many Requests");
        body.put("message", "Rate limit exceeded. Please try again later.");
        body.put("status", HttpStatus.TOO_MANY_REQUESTS.value());
        body.put("timestamp", LocalDateTime.now().toString());

        response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
        response.setContentType(MediaType.APPLICATION_JSON_VALUE);
        response.setHeader("Retry-After", "60");
        objectMapper.writeValue(response.getWriter(), body);
    }
}
