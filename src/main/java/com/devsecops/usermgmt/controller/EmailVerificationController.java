package com.devsecops.usermgmt.controller;

import com.devsecops.usermgmt.service.EmailVerificationService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Email verification endpoint.
 *
 * <p>GET /api/auth/verify?token=&lt;uuid&gt; is the link embedded in the
 * confirmation email. The token is validated and, if valid, the user's
 * {@code emailVerified} flag is set to {@code true}.</p>
 *
 * <p>Current behaviour: verification is optional — accounts work
 * whether or not the email has been confirmed. To enforce it, add a
 * check in {@link com.devsecops.usermgmt.security.UserDetailsServiceImpl}.</p>
 */
@RestController
@RequestMapping("/api/auth")
@Tag(name = "Authentication", description = "Email address verification")
public class EmailVerificationController {

    private final EmailVerificationService emailVerificationService;

    public EmailVerificationController(EmailVerificationService emailVerificationService) {
        this.emailVerificationService = emailVerificationService;
    }

    @Operation(summary = "Confirm email address using the token from the verification email")
    @GetMapping("/verify")
    public ResponseEntity<Map<String, String>> verifyEmail(@RequestParam String token) {
        emailVerificationService.verifyEmail(token);
        return ResponseEntity.ok(Map.of(
                "message", "Email address verified successfully"));
    }
}
