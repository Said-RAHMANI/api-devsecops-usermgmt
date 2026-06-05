package com.devsecops.usermgmt.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Authentication response carrying the freshly issued JWT and minimal
 * user context.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class JwtResponse {

    private String token;

    @Builder.Default
    private String type = "Bearer";

    private String username;
    private String role;

    /** Opaque refresh token for obtaining a new access token. */
    private String refreshToken;
}
