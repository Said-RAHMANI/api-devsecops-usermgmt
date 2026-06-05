package com.devsecops.usermgmt.filter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.slf4j.MDC;
import org.springframework.core.annotation.Order;
import org.springframework.stereotype.Component;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.UUID;

/**
 * Populates the SLF4J MDC for every HTTP request with fields required by ELK:
 * traceId, requestId, method, path, and execution duration.
 *
 * <p>These fields are automatically included in every JSON log line emitted
 * during the request lifecycle, enabling end-to-end request tracing in
 * Elasticsearch / Kibana without any change to existing log calls.</p>
 */
@Slf4j
@Component
@Order(0)
public class RequestLoggingFilter extends OncePerRequestFilter {

    private static final String TRACE_ID_HEADER   = "X-Trace-Id";
    private static final String REQUEST_ID_HEADER = "X-Request-Id";

    @Override
    protected void doFilterInternal(HttpServletRequest request,
                                    HttpServletResponse response,
                                    FilterChain filterChain)
            throws ServletException, IOException {

        String traceId   = resolveOrGenerate(request, TRACE_ID_HEADER);
        String requestId = resolveOrGenerate(request, REQUEST_ID_HEADER);
        long   startMs   = System.currentTimeMillis();

        MDC.put("traceId",   traceId);
        MDC.put("requestId", requestId);
        MDC.put("method",    request.getMethod());
        MDC.put("path",      request.getRequestURI());

        // Propagate traceId to the response for client-side correlation
        response.setHeader(TRACE_ID_HEADER,   traceId);
        response.setHeader(REQUEST_ID_HEADER, requestId);

        try {
            filterChain.doFilter(request, response);
        } finally {
            long duration = System.currentTimeMillis() - startMs;
            MDC.put("duration", duration + "ms");

            log.info("HTTP {} {} -> {} ({}ms)",
                    request.getMethod(),
                    request.getRequestURI(),
                    response.getStatus(),
                    duration);

            MDC.clear();
        }
    }

    private String resolveOrGenerate(HttpServletRequest request, String header) {
        String value = request.getHeader(header);
        return (value != null && !value.isBlank()) ? value : UUID.randomUUID().toString();
    }
}
