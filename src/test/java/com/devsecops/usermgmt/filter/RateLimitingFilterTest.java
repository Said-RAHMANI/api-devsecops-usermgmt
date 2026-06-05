package com.devsecops.usermgmt.filter;

import com.devsecops.usermgmt.config.RateLimitProperties;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.FilterChain;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

/**
 * Unit tests for {@link RateLimitingFilter}.
 *
 * <p>Verifies that requests within the limit are forwarded (HTTP 200 path)
 * and that requests exceeding the limit are rejected with HTTP 429.</p>
 */
class RateLimitingFilterTest {

    private RateLimitingFilter filter;
    private RateLimitProperties properties;
    private ObjectMapper objectMapper;

    @BeforeEach
    void setUp() {
        properties = new RateLimitProperties();
        // Override with very small limits for fast testing (capacity = 2)
        RateLimitProperties.EndpointLimit loginLimit = new RateLimitProperties.EndpointLimit(2, 2, 60);
        RateLimitProperties.EndpointLimit registerLimit = new RateLimitProperties.EndpointLimit(1, 1, 60);
        properties.setLogin(loginLimit);
        properties.setRegister(registerLimit);
        properties.setForgotPassword(new RateLimitProperties.EndpointLimit(1, 1, 60));

        objectMapper = new ObjectMapper();
        filter = new RateLimitingFilter(properties, objectMapper);
    }

    @Test
    void loginRequestWithinLimitIsAllowed() throws Exception {
        FilterChain chain = mock(FilterChain.class);
        MockHttpServletRequest request = loginRequest("192.168.1.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilterInternal(request, response, chain);

        verify(chain, times(1)).doFilter(request, response);
        assertThat(response.getStatus()).isEqualTo(200);
    }

    @Test
    void loginRequestExceedingLimitReturns429() throws Exception {
        String ip = "10.0.0.1";
        FilterChain chain = mock(FilterChain.class);

        // Exhaust the bucket (capacity = 2)
        for (int i = 0; i < 2; i++) {
            filter.doFilterInternal(loginRequest(ip), new MockHttpServletResponse(), chain);
        }

        // This request should be blocked
        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(loginRequest(ip), blocked, mock(FilterChain.class));

        assertThat(blocked.getStatus()).isEqualTo(429);
        assertThat(blocked.getContentAsString()).contains("Too Many Requests");
        assertThat(blocked.getHeader("Retry-After")).isEqualTo("60");
    }

    @Test
    void registerRequestExceedingLimitReturns429() throws Exception {
        String ip = "10.0.0.2";
        FilterChain chain = mock(FilterChain.class);

        // Exhaust register bucket (capacity = 1)
        filter.doFilterInternal(registerRequest(ip), new MockHttpServletResponse(), chain);

        MockHttpServletResponse blocked = new MockHttpServletResponse();
        filter.doFilterInternal(registerRequest(ip), blocked, mock(FilterChain.class));

        assertThat(blocked.getStatus()).isEqualTo(429);
    }

    @Test
    void differentIpsHaveIndependentBuckets() throws Exception {
        FilterChain chain = mock(FilterChain.class);

        // Exhaust IP-A bucket
        for (int i = 0; i < 2; i++) {
            filter.doFilterInternal(loginRequest("1.1.1.1"), new MockHttpServletResponse(), chain);
        }

        // IP-B should still be allowed
        MockHttpServletResponse responseB = new MockHttpServletResponse();
        filter.doFilterInternal(loginRequest("2.2.2.2"), responseB, chain);

        // IP-A should be blocked
        MockHttpServletResponse blockedA = new MockHttpServletResponse();
        filter.doFilterInternal(loginRequest("1.1.1.1"), blockedA, mock(FilterChain.class));

        assertThat(blockedA.getStatus()).isEqualTo(429);
        verify(chain, times(3)).doFilter(any(), any()); // 2 from IP-A + 1 from IP-B
    }

    @Test
    void nonRateLimitedPathIsNotFiltered() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/users/me");
        request.setServletPath("/api/users/me");

        assertThat(filter.shouldNotFilter(request)).isTrue();
    }

    private MockHttpServletRequest loginRequest(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/login");
        req.setServletPath("/api/auth/login");
        req.setRemoteAddr(ip);
        return req;
    }

    private MockHttpServletRequest registerRequest(String ip) {
        MockHttpServletRequest req = new MockHttpServletRequest("POST", "/api/auth/register");
        req.setServletPath("/api/auth/register");
        req.setRemoteAddr(ip);
        return req;
    }
}
