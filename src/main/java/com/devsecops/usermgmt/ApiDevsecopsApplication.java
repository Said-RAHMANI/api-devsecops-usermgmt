package com.devsecops.usermgmt;

import com.devsecops.usermgmt.config.RateLimitProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Entry point for the DevSecOps User Management API.
 *
 * <p>Reference SECURE version. Each known vulnerability that can be
 * deliberately re-introduced for the PFE study is documented inline using
 * tags INJ-01 through INJ-10 throughout the codebase.</p>
 */
@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(RateLimitProperties.class)
public class ApiDevsecopsApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiDevsecopsApplication.class, args);
    }
}
