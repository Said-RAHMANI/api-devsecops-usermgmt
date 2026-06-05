package com.devsecops.usermgmt.config;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Centralized configuration for rate limiting per endpoint.
 * Values are loaded from application.properties under the "rate-limit" prefix.
 * Registered via {@code @EnableConfigurationProperties} in SecurityConfig.
 */
@ConfigurationProperties(prefix = "rate-limit")
@Data
public class RateLimitProperties {

    private EndpointLimit login = new EndpointLimit(5, 5, 60);
    private EndpointLimit register = new EndpointLimit(3, 3, 60);
    private EndpointLimit forgotPassword = new EndpointLimit(3, 3, 60);

    @Data
    public static class EndpointLimit {
        /** Maximum number of tokens in the bucket (burst capacity). */
        private int capacity;
        /** Tokens refilled per period. */
        private int refillTokens;
        /** Refill period in seconds. */
        private int refillPeriodSeconds;

        public EndpointLimit() {}

        public EndpointLimit(int capacity, int refillTokens, int refillPeriodSeconds) {
            this.capacity = capacity;
            this.refillTokens = refillTokens;
            this.refillPeriodSeconds = refillPeriodSeconds;
        }
    }
}
